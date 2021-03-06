/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.ais.analysis.common.grid;

import dk.dma.enav.model.geometry.Position;

public class GridEqualArea {
    private static final double EARTH_RADIUS = 6371228; // [m]
    private static final double POLE_LATITUDE = 89.8; // The poles are defined in a single cell

    public double cellHeightInMeter; // The height and width of the cell
    public double latmin; // Minimum latitude for the grid
    public double latmax; // Maximum latitude for the grid
    public double lonmin; // Maximum latitude for the grid
    public double lonmax; // Maximum longitude for the grid
    public int numberOfParallelStrips; //
    public int numberOfCells; // Total number of cells in the grid
    public LatitudeStrip[] parallelStrips; // List with each parallel strip

    public GridEqualArea(double lonmin_, double latmin_, double lonmax_, double latmax_, double cellHeightInMeter_) {
        double lat;
        double cellHeightInDeg;

        cellHeightInMeter = cellHeightInMeter_;
        lonmin = lonmin_;
        lonmax = lonmax_;
        latmin = latmin_;
        latmax = latmax_;

        calcNumberOfParallelStrips();
        parallelStrips = new LatitudeStrip[numberOfParallelStrips];

        numberOfCells = 0;
        lat = latmin;
        if (lat < -POLE_LATITUDE) { // South pole cap
            lat = -POLE_LATITUDE;
            LatitudeStrip strip = new LatitudeStrip();
            strip.nColumns = 1;
            strip.latmin = latmin;
            strip.cellHeightInDeg = -POLE_LATITUDE - latmin;
            strip.cellWidthInDeg = -1;
            parallelStrips[0] = strip;
            numberOfCells = 1;
        }

        long j = 0;
        if (latmax > POLE_LATITUDE) {
            j = 1; // Make room for the polecap
        }
        int i = 0;
        for (i = numberOfCells; i <= numberOfParallelStrips - 1 - j; i++) {
            cellHeightInDeg = cellHeightInMeter * LatitudeDeg2m(lat);
            LatitudeStrip strip = new LatitudeStrip();
            strip.nColumns = calcNumberOfColumns(lat);
            strip.latmin = lat;
            strip.cellHeightInDeg = cellHeightInDeg;
            parallelStrips[i] = strip;

            double d = CalcCircumference(lat) * ((lonmax - lonmin) / 360.0);
            double cellWidthInMeter_locale = d / parallelStrips[i].nColumns; // Attempts to create a hole number of cells around a
                                                                             // latitude strip
            parallelStrips[i].cellWidthInDeg = cellWidthInMeter_locale * LongitudeDeg2m(lat);
            lat = lat + cellHeightInDeg;
            numberOfCells = numberOfCells + parallelStrips[i].nColumns;
        }

        // Create the pole cap
        if (latmax > POLE_LATITUDE) {
            LatitudeStrip strip = new LatitudeStrip();
            strip.nColumns = 1;
            strip.latmin = POLE_LATITUDE;
            strip.cellHeightInDeg = latmax - POLE_LATITUDE;
            strip.cellWidthInDeg = -1.0;
            parallelStrips[i] = strip;
            numberOfCells = numberOfCells + 1;
        }
    }

    // Get how many longitude degrees at a given latitude 1 meter is.
    public double LongitudeDeg2m(double lat) {
        double d = 0.000005164 * lat * lat * lat * lat + 0.0001753 * Math.abs(lat * lat * lat) - 0.287705412 * lat * lat
                + 0.101570737 * Math.abs(lat) + 1854.974604345;
        return 1.0 / (d * 60.0);
    }

    // Get how many latitude degrees at a given latitude 1 meter is.
    public double LatitudeDeg2m(double lat) {
        double d = -0.00005743 * Math.abs(lat * lat * lat) + 0.00777424 * lat * lat - 0.02882651 * Math.abs(lat) + 1842.98959689;
        return 1.0 / (d * 60.0);
    }

    // Calculates how many rows of cells there are between maximum latitude and minimum latitude
    private int calcNumberOfParallelStrips() {
        double lat;

        numberOfParallelStrips = 0;
        lat = latmin;
        if (lat < -POLE_LATITUDE) {
            lat = -POLE_LATITUDE;
            numberOfParallelStrips = 1;
        }
        if (lat > POLE_LATITUDE) {
            lat = POLE_LATITUDE;
        }

        while (lat < latmax && lat < POLE_LATITUDE) {
            double cellHeightInDeg = cellHeightInMeter * LatitudeDeg2m(lat);
            lat = lat + cellHeightInDeg;
            numberOfParallelStrips = numberOfParallelStrips + 1;
        }

        if (latmax > POLE_LATITUDE) {
            numberOfParallelStrips = numberOfParallelStrips + 1;
        }
        return numberOfParallelStrips;
    }

    // Calculates the number of cells in a latitude strip at a given latitude
    public int calcNumberOfColumns(double lat) {
        double lat0;

        if ((lat < -POLE_LATITUDE) || (lat >= POLE_LATITUDE)) {
            return 1;
        }

        lat0 = calcLat0(lat); // Calculates the lower border of the cell where lat is located
        double d = CalcCircumference(lat0) * ((lonmax - lonmin) / 360.0);

        if (d > cellHeightInMeter) {
            if (lonmax - lonmin == 360.0) {
                return (int) Math.floor(d / cellHeightInMeter) + 1;
            } else {
                return (int) Math.ceil(d / cellHeightInMeter);
            }
        } else {
            return 1;
        }
    }

    // Calculates the earth radius at a given latitude
    public double CalcCircumference(double lat) {
        // radius1 = majorAxis * (1 - flattening * Sin(lat / 180# * Pi) ^ 2 - 3 / 8 * flattening ^ 2 * Sin(2 * lat / 180# * Pi) ^ 2)
        // CalcCircumference = 2# * Pi * radius1
        return 2.0 * Math.PI * EARTH_RADIUS * Math.cos(lat / 180.0 * Math.PI);
    }

    // Calculates the lower latitude of the cell that contains the latitude lat
    public double calcLat0(double lat) {
        double lat0;

        if (lat >= -POLE_LATITUDE) {
            lat0 = latmin;
        } else {
            lat0 = -POLE_LATITUDE;
        }

        for (int i = 0; i <= numberOfParallelStrips - 1; i++) {
            double latPrevious = lat0;
            double cellHeightInDeg = cellHeightInMeter * LatitudeDeg2m(lat0);
            lat0 = lat0 + cellHeightInDeg;
            if (lat0 > lat) {
                lat0 = latPrevious;
                return lat0; // Break for loop
            }
        }
        return lat0;
    }

    // Calculates the cell id of a position
    // Returns -1 if it cannot be calculated
    public int getCellId(double lon, double lat) {
        if (lon < lonmin || lon > lonmax || lat < latmin || lat > latmax) {
            return -1;
        }

        int id = 0;
        if (lat < -POLE_LATITUDE) {
            return id;
        }
        if (lat > POLE_LATITUDE) {
            return numberOfCells - 1;
        }

        int Row = 0;
        if (parallelStrips[0].nColumns == 1) {
            Row = 1;
        }
        for (int i = 0; i <= numberOfParallelStrips - 1; i++) {
            if ((parallelStrips[i].latmin + parallelStrips[i].cellHeightInDeg) < lat) {
                id = id + parallelStrips[i].nColumns;
                if (i > 0) {
                    id = id + 1;
                }
                Row = i + 1;
            } else {
                i = numberOfParallelStrips; // Break the for loop
            }

        }

        int nColumns = (int) Math.floor((lon - lonmin) / (lonmax - lonmin) * parallelStrips[Row].nColumns);
        id = id + nColumns;

        return id;
    }

    // Calculates the lat,lon of a cell with id
    // return not null if all went well
    public Position getGeoPosOfCellId(int cellId) {
        double lat, lon;
        if (cellId < 0 || cellId > numberOfCells) {
            lon = 181.0;
            lat = 91.0;
            return null;
        }

        int id = 0;
        if (cellId == 0) {
            lat = latmin;
            lon = lonmin;
            return Position.create(lat, lon);
        }

        if (cellId == numberOfCells - 1) {
            lat = latmax;
            lon = latmin;
            return Position.create(lat, lon);
        }

        id = cellId;
        int i = 0;
        while (id > parallelStrips[0].nColumns - 1) {
            id -= parallelStrips[0].nColumns;
            i++;
        }
        if (id < 0) {
            id = id + parallelStrips[i - 1].nColumns;
        }
        lat = parallelStrips[i].latmin;
        lon = lonmin + parallelStrips[i].cellWidthInDeg * id;
        return Position.create(lat, lon);
    }

    // Each latitude strip is stored with the parameters below
    private class LatitudeStrip {
        int nColumns; // Number of columns in the strip
        double latmin; // The lower latitude of the strip
        double cellHeightInDeg; // The height of the latitude strip
        double cellWidthInDeg; // the width of the cells in the strip
    }
}
