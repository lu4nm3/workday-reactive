package com.workday.reactive.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import java.io.IOException;

/**
 * @author lmedina
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Summary {
    private Integer id;
    private String name;
    private Owner owner;
    private String description;
    private String url;
    private String language;

    public Summary(GHRepository repository) {
        id = repository.getId();
        name = repository.getFullName();
        description = repository.getDescription();
        url = repository.getGitTransportUrl();
        language = repository.getLanguage();
        setUser(repository);
    }

    private void setUser(GHRepository repository) {
        GHUser user = getUser(repository);
        if (user != null) {
            owner = new Owner(user.getLogin(), user.getId(), user.getHtmlUrl());
        }
    }

    private GHUser getUser(GHRepository repository) {
        try {
            return repository.getOwner();
        } catch (IOException e) {
            return null;
        }
    }
}
