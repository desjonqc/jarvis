package com.cegesoft.jarvis.service;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * Created by HoxiSword on 13/05/2020 for JARVIS
 */
public abstract class Service {

    private State state;

    public Service() {
        this.state = State.STOPPED;
    }

    public void start() {
        if (!this.state.equals(State.STOPPED))
            return;
        this.state = State.STARTING;
        try {
            startService();
            this.state = State.RUNNING;
            getLogger().info(this.getClass().getSimpleName() + " successfully started !");
        } catch (Exception e) {
            this.state = State.STOPPED;
            getLogger().warn(this.getClass().getSimpleName() + " thrown an exception while starting : " + e.getMessage());
        }
    }

    public void stop() {
        if (!this.state.equals(State.RUNNING))
            return;
        this.state = State.STOPPING;
        try {
            stopService();
            getLogger().info(this.getClass().getSimpleName() + " successfully stopped !");
        } catch (Exception e) {
            getLogger().warn(this.getClass().getSimpleName() + " thrown an exception while stopping : " + e.getMessage());
        }
        this.state = State.STOPPED;
    }

    public State getState() {
        return state;
    }

    protected abstract void startService();
    protected abstract void stopService();

    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }

}
