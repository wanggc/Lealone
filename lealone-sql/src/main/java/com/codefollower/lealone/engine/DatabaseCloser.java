/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.codefollower.lealone.engine;

import java.lang.ref.WeakReference;

import com.codefollower.lealone.message.Trace;

/**
 * This class is responsible to close a database if the application did not
 * close a connection. A database closer object only exists if there is no user
 * connected to the database.
 */
public class DatabaseCloser extends Thread {

    private final boolean shutdownHook;
    private final Trace trace;
    private volatile WeakReference<Database> databaseRef;
    private int delayInMillis;

    DatabaseCloser(Database db, int delayInMillis, boolean shutdownHook) {
        this.databaseRef = new WeakReference<Database>(db);
        this.delayInMillis = delayInMillis;
        this.shutdownHook = shutdownHook;
        trace = db.getTrace(Trace.DATABASE);
    }

    /**
     * Stop and disable the database closer. This method is called after the
     * database has been closed, or after a session has been created.
     */
    public void reset() {
        synchronized (this) {
            databaseRef = null;
        }
    }

    public void run() {
        while (delayInMillis > 0) {
            try {
                int step = 100;
                Thread.sleep(step);
                delayInMillis -= step;
            } catch (Exception e) {
                // ignore InterruptedException
            }
            if (databaseRef == null) {
                return;
            }
        }
        Database database = null;
        synchronized (this) {
            if (databaseRef != null) {
                database = databaseRef.get();
            }
        }
        if (database != null) {
            try {
                database.close(shutdownHook);
            } catch (RuntimeException e) {
                // this can happen when stopping a web application,
                // if loading classes is no longer allowed
                // it would throw an IllegalStateException
                try {
                    trace.error(e, "could not close the database");
                    // if this was successful, we ignore the exception
                    // otherwise not
                } catch (RuntimeException e2) {
                    throw e;
                }
            }
        }
    }

}
