package bunwarpj;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

public class TestCLI {


    /**
     * Main method to test and debug the bUnwarpJ API
     * for the case when we want to use bUnwarpJ from other java code without ImageJ
     * @param args
     */
    public static void main( final String[] args )
    {

        //TODO: change folder path here or have it be an input arg to main
        String dataFolder = "C:\\images-as-csv";

        try {

            List<String> csvImageFiles = findFilesOfType(dataFolder, ".csv");

            List<TestImage> targetImages = new ArrayList<>();
            List<TestImage> sourceImages = new ArrayList<>();

            for (int i=0; i< csvImageFiles.size(); i++) {
                double[][] curImageMtx = importMtxFromCsv(csvImageFiles.get(i));
                TestImage curImage = new TestImage(curImageMtx, csvImageFiles.get(i), dataFolder);

                if (csvImageFiles.get(i).contains("train")) {
                    sourceImages.add(curImage);
                } else {
                    targetImages.add(curImage);
                }
            }

            //test bUnwarpJ
            Param bParams = new Param(1, 0, 0, 2,
                    0.1, 0.1, 0, 1, 10, 0.01);

            Transformation transform = bUnwarpJ_.computeTransformationBatch(
                    targetImages.get(0).imageMtx, sourceImages.get(0).imageMtx, bParams);

            double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(transform, sourceImages.get(0).imageMtx);

            saveMtxAsPng(warpedImageMtx, "warped-source-image", dataFolder);
            saveMtxAsPng(sourceImages.get(0).imageMtx, "source-image", dataFolder);
            saveMtxAsPng(targetImages.get(0).imageMtx, "target-image", dataFolder);

        } catch (IOException e) {
            e.printStackTrace();
        }

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
    
}

class TestImage {
    double[][] imageMtx;
    String fileName;
    String containingFolder;
    boolean is2D = true;

    public TestImage(double[][] imageMtx, String fileName, String containingFolder) {
        this.imageMtx = imageMtx;
        this.fileName = fileName;
        this.containingFolder = containingFolder;
    }
}