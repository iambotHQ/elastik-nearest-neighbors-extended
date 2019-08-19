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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.UncorrelatedRandomVectorGenerator;
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
        this.nbTables = nbTables;
        this.nbBitsPerTable = nbBitsPerTable;
        this.nbDimensions = nbDimensions;
        this.description = description;

        this.bases = this.getRandomNormalVectors(nbTables, nbBitsPerTable, nbDimensions);
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
                            dotProducts[i] > 0 ? (long) Math.pow(2, i) : 0L).sum();
                    return hash;
                }
        ));
    }

    @SuppressWarnings("unchecked")
    public static LshModel fromMap(Map<String, Object> serialized) {

        LshModel lshModel = new LshModel(
                (Integer) serialized.get("_aknn_nb_tables"), (Integer) serialized.get("_aknn_nb_bits_per_table"),
                (Integer) serialized.get("_aknn_nb_dimensions"), (String) serialized.get("_aknn_description"));

        List<List<List<Double>>> basesRaw = (List<List<List<Double>>>) serialized.get("_aknn_bases");
        lshModel.bases = basesRaw.stream()
            .map(LshModel::nestedListToNestedArraysDouble)
            .map(MatrixUtils::createRealMatrix)
            .collect(Collectors.toList());

        return lshModel;
    }

    public Map<String, Object> toMap() {
        return new HashMap<String, Object>() {{
            put("_aknn_nb_tables", nbTables);
            put("_aknn_nb_bits_per_table", nbBitsPerTable);
            put("_aknn_nb_dimensions", nbDimensions);
            put("_aknn_description", description);
            put("_aknn_bases", bases.stream().map(RealMatrix::getData).collect(Collectors.toList()));
        }};
    }

    private static double[][] nestedListToNestedArraysDouble(List<List<Double>> data) {
        return data.stream()
                .map(a -> a.stream().mapToDouble(Double::doubleValue).toArray())
                .toArray(double[][]::new);
    }

    private List<RealMatrix> getRandomNormalVectors(int nbTables, int nbBitsPerTable, int nbDimensions) {
        RandomGenerator rg = new RandomDataGenerator().getRandomGenerator();
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
