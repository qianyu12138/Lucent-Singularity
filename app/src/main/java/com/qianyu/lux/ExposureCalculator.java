package com.qianyu.lux;

final class ExposureCalculator {
    private ExposureCalculator() { }

    static double shutterSeconds(double lux, double iso, double aperture) {
        requirePositive(lux, iso, aperture);
        return aperture * aperture * 250.0 / (lux * iso);
    }

    static double aperture(double lux, double iso, double seconds) {
        requirePositive(lux, iso, seconds);
        return Math.sqrt(lux * iso * seconds / 250.0);
    }

// Same unrounded EV100 calculation as the original Camera Meter capture-result path.
static double cameraEv(Float aperture, Integer iso, Long exposureNanos) {
    if(aperture==null||iso==null||exposureNanos==null||!Float.isFinite(aperture)||aperture<=0||iso<=0||exposureNanos<=0)return Double.NaN;
    return Math.log(aperture* (double)aperture/(exposureNanos/1e9))/Math.log(2)-Math.log(iso/100.0)/Math.log(2);
}

    static double targetEv(double aperture,double seconds,double iso,double nd) {
        requirePositive(aperture,seconds,iso);
        return Math.log(aperture*aperture/seconds)/Math.log(2)-Math.log(iso/100)/Math.log(2)+nd;
    }

    static double previewStops(double cameraEv,double targetEv,double calibration) {
        return cameraEv-targetEv+calibration;
    }

private static void requirePositive
(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("Exposure inputs must be finite and positive");
            }
        }
    }
}
