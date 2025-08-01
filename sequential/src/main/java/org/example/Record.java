package org.example;

public class Record extends FileReading{

    private Double la;
    private Double lo;

    public Record(Double la, Double lo) {
        this.la = Double.valueOf(la);
        this.lo = Double.valueOf(lo);
    }

    public Double getLa() {
        return la;
    }

    public Double getLo() {
        return lo;
    }

}
