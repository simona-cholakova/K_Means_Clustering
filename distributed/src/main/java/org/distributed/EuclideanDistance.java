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
        return calculate(
                record.getLa(), record.getLo(),
                cluster.getCenterLat(), cluster.getCenterLon()
        );
    }

}
