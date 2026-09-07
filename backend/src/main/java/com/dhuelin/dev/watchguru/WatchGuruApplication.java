package com.dhuelin.dev.watchguru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// For the IMDb ratings enrichment job. The job itself is off unless
// watch-guru.imdb.enabled is set, so enabling scheduling here schedules nothing
// by default.
@EnableScheduling
public class WatchGuruApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchGuruApplication.class, args);
    }

}
