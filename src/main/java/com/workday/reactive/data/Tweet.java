package com.workday.reactive.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import twitter4j.Status;

/**
 * @author lmedina
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tweet {
    private static final String TWITTER_URL = "http://twitter.com/";

    @NonNull
    private Long id;
    @NonNull
    private String url;
    @NonNull
    private String text;
    @NonNull
    private String language;

    public Tweet(Status status) {
        id = status.getId();
        url = String.format("%s%s/statuses/%d", TWITTER_URL, status.getUser().getScreenName(), id);
        text = status.getText();
        language = status.getLang();
    }
}
