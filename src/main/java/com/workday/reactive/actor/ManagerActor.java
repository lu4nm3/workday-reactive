package com.workday.reactive.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.EventBus;
import akka.japi.pf.ReceiveBuilder;
import com.workday.reactive.actor.messages.WorkAvailable;
import com.workday.reactive.actor.messages.NeedWork;
import com.workday.reactive.actor.messages.WorkDone;
import org.kohsuke.github.GHRepository;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.*;

/**
 * @author lmedina
 */
public class ManagerActor extends AbstractLoggingActor {
    private Queue<GHRepository> work;
    private Map<ActorRef, GHRepository> workMapping;
    private Map<ActorRef, ActorRef> workers;

    public static Props props() {
        return Props.create(ManagerActor.class);
    }

    ManagerActor() {
        work = new LinkedList<>();
        workMapping = new HashMap<>();
        workers = new HashMap<>();

        receive(getReceive());
    }

    private PartialFunction<Object, BoxedUnit> getReceive() {
        return ReceiveBuilder
                .match(GHRepository.class, this::addWorkToQueue)
                .match(NeedWork.class, msg -> sendWorkIfAvailable())
                .match(WorkDone.class, msg -> completeWork())
                .match(ActorRef.class, this::registerWorker)
                .match(Terminated.class, any -> handleWorkerFailure())
                .build();
    }

    private void addWorkToQueue(GHRepository repository) {
        work.add(repository);
        broadcastWorkAvailability();
    }

    private void sendWorkIfAvailable() {
        if (!work.isEmpty()) {
            GHRepository repository = work.poll();
            sender().tell(repository, self());
            workMapping.put(sender(), repository);
        }
    }

    private void completeWork() {
        workMapping.remove(sender());

        if (!work.isEmpty()) {
            broadcastWorkAvailability();
        }
    }

    private void registerWorker(ActorRef worker) {
        context().watch(worker);
        workers.put(worker, worker);

        if (!work.isEmpty()) {
            worker.tell(new WorkAvailable(), self());
        }
    }

    private void handleWorkerFailure() {
        context().unwatch(sender());
        workers.remove(sender());

        if (workMapping.containsKey(sender())) {
            work.add(workMapping.remove(sender()));
            broadcastWorkAvailability();
        }
    }

    private void broadcastWorkAvailability() {
        workers.keySet().forEach(worker -> worker.tell(new WorkAvailable(), self()));
    }
}
