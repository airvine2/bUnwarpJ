package bunwarpj;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestAPI {


    /**
     * Main method to test and debug the bUnwarpJ API
     * for the case when we want to use bUnwarpJ from other java code without ImageJ
     * @param args
     */
    public static void main( final String[] args )
    {

        //TODO: change folder path here or have it be an input arg to main
        String dataFolder_2D = "C:\\images-as-csv";
        String dataFolder_1D = "C:\\images-as-csv\\1D data";

        try {

            //test bUnwarpJ on 2D data
//            test2D(dataFolder_2D);

            //test bUnwarpJ on 1D data
            test1D(dataFolder_1D);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void test2D(String dataFolder_2D) throws IOException {
        List<String> csvImageFiles = findFilesOfType(dataFolder_2D, ".csv");

        List<TestImage> targetImages = new ArrayList<>();
        List<TestImage> sourceImages = new ArrayList<>();

        for (int i=0; i< csvImageFiles.size(); i++) {
            double[][] curImageMtx = importMtxFromCsv(csvImageFiles.get(i));
            TestImage curImage = new TestImage(curImageMtx, csvImageFiles.get(i));

            if (csvImageFiles.get(i).contains("train")) {
                sourceImages.add(curImage);
            } else if (csvImageFiles.get(i).contains("target")) {
                targetImages.add(curImage);
            }
        }

        Param bParams = new Param(1, 0, 0, 2,
                0.1, 0.1, 0, 1, 10, 0.01);

        Transformation transform = bUnwarpJ_.computeTransformationBatch(
                targetImages.get(0).imageMtx, sourceImages.get(0).imageMtx, bParams);

        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(transform, sourceImages.get(0).imageMtx);

        saveMtxAsPng(warpedImageMtx, "warped-source-image", dataFolder_2D);
        saveMtxAsPng(sourceImages.get(0).imageMtx, "source-image", dataFolder_2D);
        saveMtxAsPng(targetImages.get(0).imageMtx, "target-image", dataFolder_2D);
    }

    private static void test1D(String dataFolder) throws IOException {
        List<String> csvImageFiles = findFilesOfType(dataFolder, ".csv");

        List<TestImage> targetImages = new ArrayList<>();
        List<TestImage> sourceImages = new ArrayList<>();

        for (int i=0; i< csvImageFiles.size(); i++) {
            double[][] curImageMtx = importMtxFromCsv(csvImageFiles.get(i));
            TestImage curImage = new TestImage(curImageMtx, csvImageFiles.get(i));

            if (csvImageFiles.get(i).contains("train")) {
                sourceImages.add(curImage);
            } else if (csvImageFiles.get(i).contains("target")) {
                targetImages.add(curImage);
            }
        }

        Param bParams = new Param(1, 0, 0,
                4,
                0.0, 0.0, 0.0, 1, 0,
                0.01);

        Transformation transform = bUnwarpJ_.computeTransformationBatch(
                targetImages.get(0).imageMtx, sourceImages.get(0).imageMtx, bParams);

        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(transform, sourceImages.get(0).imageMtx);

        saveArrayCSV(warpedImageMtx[0], dataFolder + "/warped-histogram.csv", false);

    }

    private static void saveMtxAsPng(double[][] inputMtx, String fileName, String folder) {
        ImagePlus mtxAsImp = MiscTools.createImagePlusByte(inputMtx, fileName);

        FileSaver fs = new FileSaver(mtxAsImp);
        String tmpImgFile = folder + "/" + fileName + ".png";
        fs.saveAsPng(tmpImgFile);
    }

    private static List<String> findFilesOfType(String folderPath, String fileExtension) throws IOException {

        Path path = Paths.get(folderPath);

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("folderPath is not a directory");
        }

        List<String> result;
        try (Stream<Path> walk = Files.walk(path, 1)) {
            result = walk
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.toString().toLowerCase())
                    .filter(f -> f.endsWith(fileExtension))
                    .collect(Collectors.toList());
        }
        return result;

    }

    private static double[][] importMtxFromCsv(String fileName) throws IOException {
        Scanner sc = new Scanner(new File(fileName));

        List<double[]> inputList = new ArrayList<>();
        while (sc.hasNextLine())
        {
            String curLineStr = sc.nextLine();
            String[] curLine = curLineStr.split(",");

            double[] lineValues = Arrays.stream(curLine).map((x) -> Double.parseDouble(x))
                    .mapToDouble(d -> d).toArray();
            inputList.add(lineValues);
        }
        sc.close();

        double[][] result = inputList.toArray(new double[inputList.size()][]);
        return (result);
    }

    private static void saveArrayCSV(double[] aData, String aFilePath, boolean saveAsColumn) {
        try {
            FileWriter csvWriter = new FileWriter(aFilePath);

            String delimiter;
            if (saveAsColumn) {
                delimiter = "\n";
            } else {
                delimiter = ",";
            }

            for (int i=0; i < aData.length; i++) {
                csvWriter.append(String.valueOf(aData[i]));
                csvWriter.append(delimiter);
            }

            csvWriter.flush();
            csvWriter.close();
        } catch (Exception e) {
            System.out.println("Could not write csv file: " + e.getMessage());
        }
    }
}

class TestImage {
    double[][] imageMtx;
    String fileName;
    boolean is2D = true;

    public TestImage(double[][] imageMtx, String fileName) {
        this.imageMtx = imageMtx;
        this.fileName = fileName;
    }
}