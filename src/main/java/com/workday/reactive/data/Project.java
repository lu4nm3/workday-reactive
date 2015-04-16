package com.workday.reactive.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NonNull;
import org.kohsuke.github.GHRepository;
import twitter4j.Status;

import java.util.List;

/**
 * @author lmedina
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {
    @NonNull
    private GHRepository repository;

    @NonNull
    private List<Status> tweets;
}
