package com.example.jobtracker.core.constants;

import java.util.List;

public final class TrackerConstants {
    private TrackerConstants() {
    }

    public static final int JOB_ORDER_STEP = 100;

    public static final String INTERNSHIPS_BOARD_NAME = "Internships";
    public static final String FULL_TIME_JOBS_BOARD_NAME = "Full-Time Jobs";
    public static final String DEFAULT_BOARD_NAME = INTERNSHIPS_BOARD_NAME;
    public static final String COLUMN_WISH_LIST = "Wish List";
    public static final String COLUMN_APPLIED = "Applied";
    public static final String COLUMN_INTERVIEWING = "Interviewing";
    public static final String COLUMN_OFFER = "Offer";
    public static final String COLUMN_REJECTED = "Rejected";
    public static final String COLUMN_ACCEPTED = "Accepted";

    public static final List<String> DEFAULT_COLUMN_NAMES = List.of(
            COLUMN_WISH_LIST,
            COLUMN_APPLIED,
            COLUMN_INTERVIEWING,
            COLUMN_OFFER,
            COLUMN_REJECTED
    );

    public static final List<String> DEFAULT_BOARD_NAMES = List.of(
            INTERNSHIPS_BOARD_NAME,
            FULL_TIME_JOBS_BOARD_NAME
    );

    public static final String STATUS_APPLIED = "applied";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public static final String SCRAPE_RESULT_SAVED = "saved";
    public static final String SCRAPE_RESULT_DUPLICATE = "duplicate";
    public static final String SCRAPE_FALLBACK_NOT_FOUND = "Not found";
    public static final String SCRAPE_FALLBACK_NOT_LISTED = "Not listed";
    public static final String SCRAPE_FALLBACK_UNKNOWN_SOURCE = "unknown source";
}
