/*
 * Copyright [2018] [Alex Klibisz]
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
 *
 */
package org.elasticsearch.plugin.aknn;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.*;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LshModel {

    private Integer nbTables;
    private Integer nbBitsPerTable;
    private Integer nbDimensions;
    private String description;

    private List<RealMatrix> bases;
    private Long basesSeed = null;


    public LshModel(Integer nbTables, Integer nbBitsPerTable, Integer nbDimensions, String description, List<List<Double>> bases) {
        this.nbTables = nbTables;
        this.nbBitsPerTable = nbBitsPerTable;
        this.nbDimensions = nbDimensions;
        this.description = description;

        RealMatrix concatenatedTables = MatrixUtils.createRealMatrix(nestedListToNestedArraysDouble(bases));
        // assert nbBitsPerTable == concatenatedTables.getRowDimension() / nbTables; // TODO better inputted bases dimension checking

        this.bases = IntStream.range(0, nbTables)
                .map(r -> r * nbBitsPerTable)
                .mapToObj(r -> concatenatedTables.getSubMatrix(r, r + nbBitsPerTable - 1, 0, concatenatedTables.getColumnDimension() - 1))
                .collect(Collectors.toList());
    }

    public LshModel(Integer nbTables, Integer nbBitsPerTable, Integer nbDimensions, String description) {
        this(nbTables, nbBitsPerTable, nbDimensions, description, (Long) null);
    }

    public LshModel(Integer nbTables, Integer nbBitsPerTable, Integer nbDimensions, String description, Long basesSeed) {
        this.nbTables = nbTables;
        this.nbBitsPerTable = nbBitsPerTable;
        this.nbDimensions = nbDimensions;
        this.description = description;
        if(basesSeed == null) {
            Random r = new Random();
            this.basesSeed = r.nextLong();
        } else {
            this.basesSeed = basesSeed;
        }
    }

    public Map<String, Long> getVectorHashes(List<Double> queryVector) {
        return IntStream.range(0, bases.size()).mapToObj(i -> new Pair<>(Integer.toString(i), bases.get(i)))
            .collect(Collectors.toMap(
                Pair::getKey,
                basePair -> {
                    RealMatrix queryVectorAsMatrix = MatrixUtils.createColumnRealMatrix(
                            queryVector.stream().mapToDouble(Double::doubleValue).toArray());
                    double[] dotProducts = basePair.getValue().multiply(queryVectorAsMatrix).getColumn(0);
                    long hash = IntStream.range(0, dotProducts.length).mapToLong(i ->
                            dotProducts[i] >= 0 ? (long) Math.pow(2, i) : 0L).sum();
                    return hash;
                }
        ));
    }

    @SuppressWarnings("unchecked")
    public static LshModel fromMap(Map<String, Object> serialized) {

        LshModel lshModel = new LshModel(
                (Integer) serialized.get("_aknn_nb_tables"), (Integer) serialized.get("_aknn_nb_bits_per_table"),
                (Integer) serialized.get("_aknn_nb_dimensions"), (String) serialized.get("_aknn_description"),
                (Long) serialized.get("_aknn_bases_seed"));

        List<List<List<Double>>> basesRaw = (List<List<List<Double>>>) serialized.get("_aknn_bases");
        if(basesRaw != null) {
            lshModel.bases = basesRaw.stream()
                    .map(LshModel::nestedListToNestedArraysDouble)
                    .map(MatrixUtils::createRealMatrix)
                    .collect(Collectors.toList());
        }

        return lshModel;
    }

    public Map<String, Object> toMap() {
        return new HashMap<String, Object>() {{
            put("_aknn_nb_tables", nbTables);
            put("_aknn_nb_bits_per_table", nbBitsPerTable);
            put("_aknn_nb_dimensions", nbDimensions);
            put("_aknn_description", description);
            put("_aknn_bases_seed", basesSeed);
            put("_aknn_bases", bases != null ? bases.stream().map(RealMatrix::getData).collect(Collectors.toList()) : null);
        }};
    }

    private static double[][] nestedListToNestedArraysDouble(List<List<Double>> data) {
        return data.stream()
                .map(a -> a.stream().mapToDouble(Double::doubleValue).toArray())
                .toArray(double[][]::new);
    }

    public void generateBases(int nbDimensions) {
        this.nbDimensions = nbDimensions;
        this.bases = this.getRandomNormalVectors(nbTables, nbBitsPerTable, nbDimensions);
    }

    public boolean hasBases() {
        return this.bases != null;
    }

    private List<RealMatrix> getRandomNormalVectors(int nbTables, int nbBitsPerTable, int nbDimensions) {
        RandomGenerator rg = new RandomDataGenerator().getRandomGenerator();
        rg.setSeed(this.basesSeed);
        GaussianRandomGenerator scalarGenerator = new GaussianRandomGenerator(rg);
        UncorrelatedRandomVectorGenerator vectorGenerator = new UncorrelatedRandomVectorGenerator(nbDimensions, scalarGenerator);

        ArrayList<RealMatrix> matrices = new ArrayList<>();
        for(int i = 0; i < nbTables; i++) {
            double[][] d = new double[nbBitsPerTable][];
            for(int j = 0; j < nbBitsPerTable; j++) {
                d[j] = vectorGenerator.nextVector();
            }
            matrices.add(MatrixUtils.createRealMatrix(d));
        }
        return matrices;
    }
}
