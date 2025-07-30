package org.distributed;

import java.io.Serializable;

public class ClusterCenter implements Serializable {
    public double lat, lon;

    public ClusterCenter(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }
}
