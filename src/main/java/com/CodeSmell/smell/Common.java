package com.CodeSmell.smell;

import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.CodePropertyGraph.Relation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.model.Pair;
import com.CodeSmell.model.ClassRelation.RelationshipType;
import com.CodeSmell.stat.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Common {

    public static StatTracker stats;

    protected static HashMap<CPGClass, ArrayList<CPGClass>> interfaces;

    // temporary function to simplify running smell test routine
    public static Stream<Smell> buildSmellStream(CodePropertyGraph cpg) {
        OrphanVariable s1  = new OrphanVariable(cpg);
        FeatureEnvy s2  = new FeatureEnvy(cpg);
        GodClass s3 = new GodClass(cpg);
        InappropriateIntimacy s4 = new InappropriateIntimacy(cpg);
        ISPViolation s5 = new ISPViolation(cpg);
        OrphanVariable s6 = new OrphanVariable(cpg);
        RefusedBequest s7 = new RefusedBequest(cpg);
        Stream.Builder<Smell> sb = Stream.builder();
        sb.add(s1);
        sb.add(s2);
        sb.add(s3);
        sb.add(s4);
        sb.add(s5);
        sb.add(s6);
        sb.add(s7);
        return sb.build();
    }

    public static void initStatTracker(CodePropertyGraph cpg) {
        stats = new StatTracker(cpg);
        List<CPGClass> ifaceClasses = stats
                .distinctClassTypes.get(ClassType.INTERFACE);
        List<Relation> realizations = stats
                .distinctRelations.get(RelationshipType.REALIZATION);
        Common.interfaces = new HashMap<>();

        for (CPGClass iface : ifaceClasses) {
            ArrayList<CPGClass> realizors = realizations.stream()
                    .filter(r -> r.destination == iface)
                    .map(r -> r.source)
                    .collect(Collectors.toCollection(ArrayList::new));
            Common.interfaces.put(iface, realizors);
        }
    }

    public static CPGClass findClassByName(CodePropertyGraph cpg,
            String name) {
        return cpg.getClasses()
                .stream()
                .filter(c -> c.classFullName.equals(name))
                .findAny()
                .orElse(null);
    }

    public static MethodStat getMethodStats(Method m) {
        for (MethodStat ms : stats.methodStats.values()) {
            if (ms.method == m) {
                return ms;
            }
        }
        throw new IllegalArgumentException("No method stats for method " + m);
    }

    // returns the list of methods declared within the interface
    // which class c extends
    public static Method[] interfaceMethods(CPGClass c) {

        CPGClass iface = c.getInheritsFrom().get(0);
        Set<String> ifaceMethods = iface.getMethods()
                .stream()
                .map(m -> m.name)
                .collect(Collectors.toSet());
        return c.getMethods()
                .stream()
                .filter(m -> ifaceMethods.contains(m.name))
                .collect(Collectors.toCollection(ArrayList::new))
                .toArray(new Method[0]);
    }

    // returns the method objects for the interface of a classifier
    public static Method[] originalInterfaceMethods(CPGClass c) {
        CPGClass iface = c.getInheritsFrom().get(0);
        return iface.getMethods().toArray(new Method[0]);
    }

    // returns true if c2 is a nested class (within the same file) of c
    public static boolean isNestedClass(CPGClass c2, CPGClass c) {
        return c.classFullName.startsWith(c + ".");
    }

    // return classes where nested classes are values in hashmap
    public static HashMap<CPGClass, CPGClass[]> collapseNestedClasses(
            List<CPGClass> classes) {
        HashMap<CPGClass, CPGClass[]> collapsedClasses = new HashMap();
        for (CPGClass c : classes) {
            ArrayList<CPGClass> nestedClasses = new ArrayList<>();
            for (CPGClass c2 : classes) {
                if (c2 != c && isNestedClass(c2, c)) {
                    nestedClasses.add(c2);
                }
            }
            collapsedClasses.put(c, nestedClasses.toArray(new CPGClass[0]));
        }
        return collapsedClasses;
    }

    protected static abstract class ClassSorter {
        private ArrayList<Pair<CPGClass, Integer>> items = new ArrayList<>();
        private int total = 0;

        public int getTotal() {
            return this.total;
        }

        protected abstract int order(CPGClass c);

        // returns the sorted key values of the hash set of nested classes
        public void sortNested(HashMap<CPGClass, CPGClass[]> classes) {
            HashMap<CPGClass, Integer> nestedValues = new HashMap<>();
            Comparator<Map.Entry<CPGClass, CPGClass[]>> comparator = new
                    Comparator<>() {
                        public int compare(HashMap.Entry e1, HashMap.Entry e2) {
                            CPGClass k1 = (CPGClass) e1.getKey();
                            CPGClass[] v1 = (CPGClass[]) e1.getValue();
                            CPGClass k2 = (CPGClass) e2.getKey();
                            CPGClass[] v2 = (CPGClass[]) e1.getValue();

                            int sum1 = order(k1);
                            for (CPGClass c : v1) {
                                sum1 += order(c);
                            }

                            int sum2 = order(k2);
                            for (CPGClass c : v2) {
                                sum2 += order(c);
                            }
                            nestedValues.put(k1, sum1);
                            nestedValues.put(k2, sum2);
                            return sum1 - sum2;
                        }
                    };

            ArrayList<Map.Entry<CPGClass, CPGClass[]>> classList = new ArrayList<>
                    (classes.entrySet());
            Collections.sort(classList, comparator);
            classList.forEach((e) -> {
                CPGClass c = e.getKey();
                int val = nestedValues.get(c);
                this.items.add(new Pair(c, val));
                this.total += val;
            });
        }

        public int getIndex(CPGClass c) {
            for (int i = 0; i < items.size(); i++) {
                if (c == items.get(i).first) {
                    return i;
                }
            }
            return -1;
        }

        public CPGClass getKey(int i) {
            if (i < 0 || i >= items.size()) {
                throw new IllegalArgumentException("bad sorter index");
            }
            return items.get(i).first;
        }

        public int getVal(int i) {
            if (i < 0 || i >= items.size()) {
                throw new IllegalArgumentException("bad sorter index");
            }
            return items.get(i).second;
        }

        public int itemCount() {
            return items.size();
        }
    }

    public static class RelationSorter extends ClassSorter {
        protected int order(CPGClass c) {
            return c.getOutwardRelations().size();
        }
    }


    public static class ContentSorter extends ClassSorter {
        protected int order(CPGClass c) {
            return contentSize(c);
        }

        private int contentSize(CPGClass c) {
            return stats.classStats.get(c).totalClassLines;
        }
    }
}
