package com.cegesoft.jarvis.utils;

import com.cegesoft.jarvis.properties.IAutoSerializable;
import com.cegesoft.jarvis.properties.SavedItem;

import java.util.Objects;

/**
 * Created by HoxiSword on 14/04/2020 for JARVIS
 */
public class Tuple<A, B> implements IAutoSerializable {

    @SavedItem
    private A a;
    @SavedItem
    private B b;

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    private Tuple() {}

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tuple)) return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(a, tuple.a) &&
                Objects.equals(b, tuple.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }

    @Override
    public IAutoSerializable getBase() {
        return this;
    }
}
