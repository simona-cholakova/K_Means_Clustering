package org.distributed;

import java.io.Serializable;

public class Record extends FileReading implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private double capacity;
    private Double la;
    private Double lo;

    public Record(Double la, Double lo) {
        this.la = Double.valueOf(la);
        this.lo = Double.valueOf(lo);
    }

    public String getName() {
        return name;
    }

    public double getCapacity() {
        return capacity;
    }

    public Double getLa() {
        return la;
    }

    public Double getLo() {
        return lo;
    }

}
