package com.oracle.st.pm.json.movieTicketing.transientObjects;

import com.google.gson.Gson;

import com.google.gson.GsonBuilder;

import com.oracle.st.pm.json.movieTicketing.docStore.Movie;
import com.oracle.st.pm.json.movieTicketing.docStore.Screening;
import com.oracle.st.pm.json.movieTicketing.docStore.Theater;

import com.oracle.st.pm.json.movieTicketing.qbe.BetweenOperator;

import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import java.util.Iterator;
import java.util.List;

import oracle.soda.OracleCollection;
import oracle.soda.OracleCursor;
import oracle.soda.OracleDatabase;
import oracle.soda.OracleDocument;
import oracle.soda.OracleException;
import oracle.soda.OracleOperationBuilder;

public class TheatersByMovie {

    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();

    private Movie movie;
    private TheaterWithShowTimes[] theaters;


    public class ShowingsByMovieAndDate {

        private int movieId;
        private BetweenOperator startTime;

        public ShowingsByMovieAndDate(int movieId, Calendar date) {
            this.movieId = movieId;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            Calendar temp = (Calendar) date.clone();
            temp.set(Calendar.HOUR_OF_DAY, 0);
            temp.set(Calendar.MINUTE, 0);
            temp.set(Calendar.SECOND, 0);
            temp.set(Calendar.MILLISECOND, 0);
            String start = sdf.format(temp.getTime());
            temp.set(Calendar.HOUR_OF_DAY, 23);
            temp.set(Calendar.MINUTE, 59);
            temp.set(Calendar.SECOND, 59);
            temp.set(Calendar.MILLISECOND, 999);
            String end = sdf.format(temp.getTime());
            this.startTime = new BetweenOperator(start, end);
        }
    }

    private class TheaterWithShowTimes {
        private Theater theater;
        private transient HashMap<Integer, Screen> screenList = new HashMap<Integer, Screen>();
        private Screen[] screens;

        private TheaterWithShowTimes(Theater theater) {
            this.theater = theater;
        }

        private void addScreening(String key, Screening screening) {
            Screen screen = null;
            int screenId = screening.getScreenId();
            if (!this.screenList.containsKey(screenId)) {
                screen = new Screen(screenId);
                this.screenList.put(screenId, screen);
            } else {
                screen = this.screenList.get(screenId);
            }
            screen.addShowTime(key, screening);
        }

        private void fixLists() {
            this.screens = this.screenList.values().toArray(new Screen[0]);
            this.screenList = null;
            for (int i = 0; i < screens.length; i++) {
                screens[i].fixLists();
            }
        }
    }

    private class Screen {
        private int id;
        private transient List showTimeList = new ArrayList<ShowTime>();
        private ShowTime[] showTimes;

        private Screen(int id) {
            this.id = id;
        }

        private void addShowTime(String key, Screening screening) {
            this.showTimeList.add(new ShowTime(key, screening.getStartTime(), screening.getSeatsRemaining()));
        }

        private void fixLists() {
            this.showTimes = (ShowTime[]) this.showTimeList.toArray(new ShowTime[0]);
            this.showTimeList = null;
        }
    }

    private class ShowTime {
        private String id;
        private Date startTime;
        private int seatsRemaining;

        private ShowTime(String id, Date startTime, int seatsRemaining) {
            this.id = id;
            this.startTime = startTime;
            this.seatsRemaining = seatsRemaining;
        }
    }

    private void fixLists() {
        for (int i = 0; i < theaters.length; i++) {
            theaters[i].fixLists();
        }
    }

    public TheaterWithShowTimes[] getShowTimesByMovieAndDate(OracleDatabase db, Date date) throws OracleException {

        HashMap<Integer, TheaterWithShowTimes> theaterCache = new HashMap<Integer, TheaterWithShowTimes>();

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        // Use QBE to get the set of showings for the specifed Theater on the specified Date.

        OracleCollection screenings = db.openCollection(Screening.COLLECTION_NAME);
        ShowingsByMovieAndDate qbeDefinition = (new ShowingsByMovieAndDate(this.movie.getMovieId(), cal));
        OracleDocument qbe = db.createDocumentFromString(gson.toJson(qbeDefinition));
        OracleOperationBuilder screeningDocuments = screenings.find().filter(qbe);
        // System.out.println(qbe.getContentAsString() + ": " + screeningDocuments.count());

        // Build a structure containg one entry for each Movie. Add the informaton about the screenings to the Movie.

        OracleCursor screeningCursor = screeningDocuments.getCursor();

        while (screeningCursor.hasNext()) {
            OracleDocument doc = screeningCursor.next();
            Screening screening = Screening.fromJson(doc.getContentAsString());
            String key = doc.getKey();
            int theaterId = screening.getTheaterId();
            TheaterWithShowTimes theater = null;
            if (!theaterCache.containsKey(theaterId)) {
                Theater nextTheater = Theater.fromJson(Theater.getTheaterById(db, theaterId).getContentAsString());
                theater = new TheaterWithShowTimes(nextTheater);
                theaterCache.put(theaterId, theater);
            } else {
                theater = theaterCache.get(theaterId);
            }
            theater.addScreening(key, screening);
        }
        return theaterCache.values().toArray(new TheaterWithShowTimes[0]);
    }

    public TheatersByMovie(OracleDatabase db, String key, Date date) throws OracleException, IOException {
        this.movie = Movie.fromJson(Movie.getMovie(db, key).getContentAsString());
        this.theaters = getShowTimesByMovieAndDate(db, date);
        fixLists();
    }

    public String toJSON() {
        return gson.toJson(this);
    }

}