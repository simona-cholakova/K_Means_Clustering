package org.distributed;

public class EuclideanDistance implements Distance<Record, Cluster> {

    public double calculate(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = lat1 - lat2;
        double deltaLon = lon1 - lon2;
        return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon);
    }

    //calculates the distance between a Record and a Cluster
    @Override
    public double calculate(Record record, Cluster cluster) {
        double lat1 = record.getLa();
        double lon1 = record.getLo();

        double lat2 = cluster.getCenterLat();
        double lon2 = cluster.getCenterLon();

        double deltaLat = lat1 - lat2;
        double deltaLon = lon1 - lon2;

        //apply the Euclidean formula for distance with ignoring Earth's curvature
        return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon);
    }

}
