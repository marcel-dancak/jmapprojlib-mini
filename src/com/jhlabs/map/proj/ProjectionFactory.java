/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
/**
Bernhard Jenny, Institute of Cartography, ETH Zurich:
Added private static Hashtable nameMap which maps between human-readable
projection names and proj4 names.
Changed ProjectionFactoryregister() to fill the new nameMap.
Changed initialize() to instantiate nameMap.
Added method getNamedProjection() to first map from a human-readable name to a
proj4 name and then to a Projection class.
Changed register(): the human-readable name of a projection is not passed anymore
to register(), but the name is retrieved from an instance of the projection. This
avoids inconsistencies between names returned by the projection and names passed
to register().
 * 20 October 2010: modified readProjectionFile such that opened file with
 * coordinate system specification is always closed when the method exits.
 */
package com.jhlabs.map.proj;

import java.io.*;
import java.util.*;
import com.jhlabs.map.*;
import java.awt.geom.Point2D;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProjectionFactory {

    private final static double SIXTH = .1666666666666666667; /* 1/6 */

    private final static double RA4 = .04722222222222222222; /* 17/360 */

    private final static double RA6 = .02215608465608465608; /* 67/3024 */

    private final static double RV4 = .06944444444444444444; /* 5/72 */

    private final static double RV6 = .04243827160493827160; /* 55/1296 */

    private static AngleFormat format = new AngleFormat(AngleFormat.ddmmssPattern, true);

    /**
     * Return a projection initialized with a PROJ.4 argument list.
     */
    public static Projection fromPROJ4Specification(String[] args) {
        Projection projection = null;
        Ellipsoid ellipsoid = null;
        double a = 0, b = 0, es = 0;

        Hashtable params = new Hashtable();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("+")) {
                int index = arg.indexOf('=');
                if (index != -1) {
                    String key = arg.substring(1, index);
                    String value = arg.substring(index + 1);
                    params.put(key, value);
                }
            }
        }

        String s;
        s = (String) params.get("proj");
        if (s != null) {
            projection = getNamedPROJ4Projection(s);
            if (projection == null) {
                throw new ProjectionException("Unknown projection: " + s);
            }
        }

        s = (String) params.get("init");
        if (s != null) {
            projection = getNamedPROJ4CoordinateSystem(s);
            if (projection == null) {
                throw new ProjectionException("Unknown projection: " + s);
            }
            a = projection.getEquatorRadius();
            es = projection.getEllipsoid().getEccentricitySquared();
        }

        // Set the ellipsoid
        String ellipsoidName = "";
        s = (String) params.get("R");
        if (s != null) {
            a = Double.parseDouble(s);
        } else {
            s = (String) params.get("ellps");
            if (s == null) {
                s = (String) params.get("datum");
            }
            if (s != null) {
                Ellipsoid[] ellipsoids = Ellipsoid.ellipsoids;
                for (int i = 0; i < ellipsoids.length; i++) {
                    if (ellipsoids[i].shortName.equals(s)) {
                        ellipsoid = ellipsoids[i];
                        break;
                    }
                }
                if (ellipsoid == null) {
                    throw new ProjectionException("Unknown ellipsoid: " + s);
                }
                es = ellipsoid.eccentricity2;
                a = ellipsoid.equatorRadius;
                ellipsoidName = s;
            } else {
                s = (String) params.get("a");
                if (s != null) {
                    a = Double.parseDouble(s);
                }
                s = (String) params.get("es");
                if (s != null) {
                    es = Double.parseDouble(s);
                } else {
                    s = (String) params.get("rf");
                    if (s != null) {
                        es = Double.parseDouble(s);
                        es = es * (2. - es);
                    } else {
                        s = (String) params.get("f");
                        if (s != null) {
                            es = Double.parseDouble(s);
                            es = 1.0 / es;
                            es = es * (2. - es);
                        } else {
                            s = (String) params.get("b");
                            if (s != null) {
                                b = Double.parseDouble(s);
                                es = 1. - (b * b) / (a * a);
                            }
                        }
                    }
                }
                if (b == 0) {
                    b = a * Math.sqrt(1. - es);
                }
            }

            s = (String) params.get("R_A");
            if (s != null && Boolean.getBoolean(s)) {
                a *= 1. - es * (SIXTH + es * (RA4 + es * RA6));
            } else {
                s = (String) params.get("R_V");
                if (s != null && Boolean.getBoolean(s)) {
                    a *= 1. - es * (SIXTH + es * (RV4 + es * RV6));
                } else {
                    s = (String) params.get("R_a");
                    if (s != null && Boolean.getBoolean(s)) {
                        a = .5 * (a + b);
                    } else {
                        s = (String) params.get("R_g");
                        if (s != null && Boolean.getBoolean(s)) {
                            a = Math.sqrt(a * b);
                        } else {
                            s = (String) params.get("R_h");
                            if (s != null && Boolean.getBoolean(s)) {
                                a = 2. * a * b / (a + b);
                                es = 0.;
                            } else {
                                s = (String) params.get("R_lat_a");
                                if (s != null) {
                                    double tmp = Math.sin(parseAngle(s));
                                    if (Math.abs(tmp) > MapMath.HALFPI) {
                                        throw new ProjectionException("-11");
                                    }
                                    tmp = 1. - es * tmp * tmp;
                                    a *= .5 * (1. - es + tmp) / (tmp * Math.sqrt(tmp));
                                    es = 0.;
                                } else {
                                    s = (String) params.get("R_lat_g");
                                    if (s != null) {
                                        double tmp = Math.sin(parseAngle(s));
                                        if (Math.abs(tmp) > MapMath.HALFPI) {
                                            throw new ProjectionException("-11");
                                        }
                                        tmp = 1. - es * tmp * tmp;
                                        a *= Math.sqrt(1. - es) / tmp;
                                        es = 0.;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        projection.setEllipsoid(new Ellipsoid(ellipsoidName, a, es, ellipsoidName));

        // Other arguments
//		projection.setProjectionLatitudeDegrees( 0 );
//		projection.setProjectionLatitude1Degrees( 0 );
//		projection.setProjectionLatitude2Degrees( 0 );
        s = (String) params.get("lat_0");
        if (s != null) {
            projection.setProjectionLatitudeDegrees(parseAngle(s));
        }
        s = (String) params.get("lon_0");
        if (s != null) {
            projection.setProjectionLongitudeDegrees(parseAngle(s));
        }
        s = (String) params.get("lat_ts");
        if (s != null) {
            projection.setTrueScaleLatitudeDegrees(parseAngle(s));
        }
        s = (String) params.get("x_0");
        if (s != null) {
            projection.setFalseEasting(Double.parseDouble(s));
        }
        s = (String) params.get("y_0");
        if (s != null) {
            projection.setFalseNorthing(Double.parseDouble(s));
        }

        s = (String) params.get("k_0");
        if (s == null) {
            s = (String) params.get("k");
        }
        if (s != null) {
            projection.setScaleFactor(Double.parseDouble(s));
        }

        s = (String) params.get("units");
        if (s != null) {
            Unit unit = Units.findUnits(s);
            if (unit != null) {
                projection.setFromMetres(1.0 / unit.value);
            }
        }
        s = (String) params.get("to_meter");
        if (s != null) {
            projection.setFromMetres(1.0 / Double.parseDouble(s));
        }

        if (projection instanceof TransverseMercatorProjection) {
            s = (String) params.get("zone");
            if (s != null) {
                ((TransverseMercatorProjection) projection).setUTMZone(Integer.parseInt(s));
            }
        }

//zone
//towgs84
//alpha
//datum
//lat_ts
//azi
//lonc
//rf
//pm

        projection.initialize();

        return projection;
    }

    private static double parseAngle(String s) {
        return format.parse(s, null).doubleValue();
    }
    private static Hashtable registry;
    private static Hashtable nameMap;

    private static void register(String proj4Name, Class cls) throws InstantiationException, IllegalAccessException {
        try {
            registry.put(proj4Name, cls);
            Projection projection = (Projection) cls.newInstance();
            String readableName = projection.getName();
            nameMap.put(readableName, proj4Name);
        } catch (InstantiationException e) {
            System.err.println("unable to register " + proj4Name);
            throw e;
        }
    }

    public static Projection getNamedProjection(String name) {
        if (registry == null) {
            initialize();
        }
        String proj4Name = (String) nameMap.get(name);
        return getNamedPROJ4Projection(proj4Name);
    }

    public static Projection getNamedPROJ4Projection(String name) {
        if (registry == null) {
            initialize();
        }
        Class cls = (Class) registry.get(name);
        if (cls != null) {
            try {
                Projection projection = (Projection) cls.newInstance();
                if (projection != null) {
                    projection.setName(name); // is this needed ? FIXME
                }
                return projection;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Object[] getOrderedProjectionNames() {
        if (registry == null) {
            initialize();
        }
        Object[] names = nameMap.keySet().toArray();
        Arrays.sort(names);
        return names;
    }

    private static void initialize() {
        try {
            registry = new Hashtable();
            nameMap = new Hashtable();

            register("merc", MercatorProjection.class);
            register("omerc", ObliqueMercatorProjection.class);
            register("tmerc", TransverseMercatorProjection.class);
            register("utm", TransverseMercatorProjection.class);
        } catch (InstantiationException ex) {
            Logger.getLogger(ProjectionFactory.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(ProjectionFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static Projection readProjectionFile(String file, String name) throws IOException {

        BufferedReader reader = null;
        try {
            String filePath = "/coordsys/" + file;
            InputStream is = ProjectionFactory.class.getResourceAsStream(filePath);
            reader = new BufferedReader(new InputStreamReader(is));
            StreamTokenizer t = new StreamTokenizer(reader);
            t.commentChar('#');
            t.ordinaryChars('0', '9');
            t.ordinaryChars('.', '.');
            t.ordinaryChars('-', '-');
            t.ordinaryChars('+', '+');
            t.wordChars('0', '9');
            t.wordChars('\'', '\'');
            t.wordChars('"', '"');
            t.wordChars('_', '_');
            t.wordChars('.', '.');
            t.wordChars('-', '-');
            t.wordChars('+', '+');
            t.wordChars(',', ',');
            t.nextToken();

            while (t.ttype == '<') {
                t.nextToken();
                if (t.ttype != StreamTokenizer.TT_WORD) {
                    throw new IOException(t.lineno() + ": Word expected after '<'");
                }

                String cname = t.sval;
                t.nextToken();
                if (t.ttype != '>') {
                    throw new IOException(t.lineno() + ": '>' expected");
                }
                t.nextToken();
                Vector v = new Vector();
                while (t.ttype != '<') {
                    if (t.ttype == '+') {
                        t.nextToken();
                    }
                    if (t.ttype != StreamTokenizer.TT_WORD) {
                        throw new IOException(t.lineno() + ": Word expected after '+'");
                    }
                    String key = t.sval;
                    t.nextToken();
                    if (t.ttype == '=') {
                        t.nextToken();
                        //Removed check to allow for proj4 hack +nadgrids=@null
                        //if ( t.ttype != StreamTokenizer.TT_WORD )
                        //	throw new IOException( t.lineno()+": Value expected after '='" );
                        String value = t.sval;
                        t.nextToken();
                        if (key.startsWith("+")) {
                            v.add(key + "=" + value);
                        } else {
                            v.add("+" + key + "=" + value);
                        }
                    }
                }
                t.nextToken();
                if (t.ttype != '>') {
                    throw new IOException(t.lineno() + ": '<>' expected");
                }
                t.nextToken();
                if (cname.equals(name)) {
                    String[] args = new String[v.size()];
                    v.copyInto(args);
                    return fromPROJ4Specification(args);
                }
            }
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

    }

    public static Projection getNamedPROJ4CoordinateSystem(String name) {
        String[] files = {
            "world",
            "nad83",
            "nad27",
            "esri",
            "epsg",};

        try {
            int p = name.indexOf(':');
            if (p >= 0) {
                return readProjectionFile(name.substring(0, p), name.substring(p + 1));
            }

            for (int i = 0; i < files.length; i++) {
                Projection projection = readProjectionFile(files[i], name);
                if (projection != null) {
                    return projection;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        Projection projection = ProjectionFactory.fromPROJ4Specification(args);


        if (projection != null) {
            System.out.println(projection.getPROJ4Description());

            for (int i = 0; i
                    < args.length; i++) {
                String arg = args[i];


                if (!arg.startsWith("+") && !arg.startsWith("-")) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(new File(args[i])));
                        Point2D.Double p = new Point2D.Double();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            StringTokenizer t = new StringTokenizer(line, " ");
                            String slon = t.nextToken();
                            String slat = t.nextToken();
                            p.x = format.parse(slon, null).doubleValue();
                            p.y = format.parse(slat, null).doubleValue();
                            projection.transform(p, p);
                            System.out.println(p.x + " " + p.y);
                        }
                    } catch (IOException e) {
                        System.out.println("IOException: " + args[i] + ": " + e.getMessage());
                    }
                }
            }
        } else {
            System.out.println("Can't find projection " + args[0]);
        }

    }
}
