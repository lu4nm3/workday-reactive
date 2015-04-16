package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;
import com.workday.reactive.actor.messages.NeedWork;
import com.workday.reactive.actor.messages.NewWorker;
import com.workday.reactive.actor.messages.WorkAvailable;
import com.workday.reactive.actor.messages.WorkDone;
import org.kohsuke.github.GHRepository;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author lmedina
 */
public class ManagerActor extends AbstractLoggingActor {
    private Queue<GHRepository> work;
    private Map<ActorRef, GHRepository> workerRepositoryMapping;
    private Map<ActorRef, ActorRef> workers;

    public static Props props() {
        return Props.create(ManagerActor.class);
    }

    ManagerActor() {
        work = new LinkedList<>();
        workerRepositoryMapping = new HashMap<>();
        workers = new HashMap<>();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return ReceiveBuilder
                .match(GHRepository.class, this::addWorkToQueue)
                .match(NeedWork.class, msg -> sendWorkIfAvailable())
                .match(WorkDone.class, msg -> completeWork())
                .match(NewWorker.class, msg -> registerWorker())
                .match(Terminated.class, any -> handleWorkerFailure())
                .build();
    }

    private void addWorkToQueue(GHRepository repository) {
        log().debug("Received new reactive repository \"{}\".", repository.getFullName());
        work.add(repository);
        broadcastWorkAvailability();
    }

    private void sendWorkIfAvailable() {
        if (!work.isEmpty()) {
            GHRepository repository = work.poll();
            sender().tell(repository, self());
            workerRepositoryMapping.put(sender(), repository);
        }
    }

    private void completeWork() {
        workerRepositoryMapping.remove(sender());

        if (!work.isEmpty()) {
            broadcastWorkAvailability();
        }
    }

    private void registerWorker() {
        context().watch(sender());
        workers.put(sender(), sender());

        if (!work.isEmpty()) {
            sender().tell(new WorkAvailable(), self());
        }
    }

    private void handleWorkerFailure() {
        context().unwatch(sender());
        workers.remove(sender());

        if (workerRepositoryMapping.containsKey(sender())) {
            work.add(workerRepositoryMapping.remove(sender()));
            broadcastWorkAvailability();
        }
    }

    private void broadcastWorkAvailability() {
        workers.keySet().forEach(worker -> worker.tell(new WorkAvailable(), self()));
    }
}
