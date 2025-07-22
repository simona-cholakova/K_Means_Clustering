package org.parallel;

public class EuclideanDistance implements Distance<Record, Cluster> {

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
