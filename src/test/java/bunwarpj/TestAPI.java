package bunwarpj;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
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
            test2D(dataFolder_2D);

            //test bUnwarpJ on 1D data
//            test1D(dataFolder_1D);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void test2D(String dataFolder_2D) throws IOException {

        List<String> csvImageFiles = findFilesOfType(dataFolder_2D, ".csv");

        List<TestImage> targetImages = new ArrayList<>();
        List<TestImage> sourceImages = new ArrayList<>();

        for (int i=0; i< csvImageFiles.size(); i++) {
            int[][] curImageMtx = importMtxFromCsv(csvImageFiles.get(i));
            TestImage curImage = new TestImage(curImageMtx, csvImageFiles.get(i));

            if (csvImageFiles.get(i).contains("train")) {
                sourceImages.add(curImage);
            } else if (csvImageFiles.get(i).contains("target")) {
                targetImages.add(curImage);
            }
        }

        Param bParams = new Param(2, 0, 0, 2,
                0.1, 0.1, 0, 1, 10, 0.01);

        String outputFolder = dataFolder_2D + "\\results";

        int[][] trainMtx = sourceImages.get(0).imageMtx;
        saveMtxAsPng(trainMtx, "source-image", outputFolder);

        for (int i=0; i< targetImages.size(); i++) {

            int[][] targetMtx = targetImages.get(i).imageMtx;
            saveMtxAsPng(targetMtx, "target-image_" + i, outputFolder);

            String outFileTag = "NEW_" + i;

            //new method
            Transformation tResult = bUnwarpJ_.computeTransformationBatch(
                targetMtx, trainMtx, bParams);

            //existing method - copied code from UI
//            Transformation tResult = guiRegistration(trainMtx, targetMtx, bParams);


            //existing api method
//            ImagePlus targetImage = createBWImagePlusUsingExternalMtx(targetMtx);
//            ImagePlus trainImage = createBWImagePlusUsingExternalMtx(trainMtx);
//            Transformation tResult = bUnwarpJ_.computeTransformationBatch(targetImage,
//                    trainImage, null, null, bParams);


            //save results of registration
            saveTransformationResult(tResult, outputFolder, outFileTag, trainMtx);

        }
    }

    private static void test1D(String dataFolder) throws IOException {
        List<String> csvImageFiles = findFilesOfType(dataFolder, ".csv");

        List<TestImage> targetImages = new ArrayList<>();
        List<TestImage> sourceImages = new ArrayList<>();

        for (int i=0; i< csvImageFiles.size(); i++) {
            int[][] curImageMtx = importMtxFromCsv(csvImageFiles.get(i));
            TestImage curImage = new TestImage(curImageMtx, csvImageFiles.get(i));

            if (csvImageFiles.get(i).contains("train")) {
                sourceImages.add(curImage);
            } else if (csvImageFiles.get(i).contains("target")) {
                targetImages.add(curImage);
            }
        }

        Param bParams = new Param(1, 1, 0,
                4,
                0.0, 0.0, 0.0, 1, 0,
                0.01);

//        Transformation transform = bUnwarpJ_.computeTransformationBatch(
//                targetImages.get(0).imageMtx, sourceImages.get(0).imageMtx, bParams);
//
//        double[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(transform, sourceImages.get(0).imageMtx);
//
//        saveArrayCSV(warpedImageMtx[0], dataFolder + "/warped-histogram.csv", false);

    }

    private static void saveTransformationResult(Transformation tResult, String outputFolder, String fileName,
                                                 int[][] sourceMtx) {
        int intervals = tResult.getIntervals();
        double[][] cx = tResult.getDirectDeformationCoefficientsX();
        double[][] cy = tResult.getDirectDeformationCoefficientsY();

        MiscTools.saveElasticTransformation(intervals, cx, cy, outputFolder + "/transformCoeffs_" + fileName + ".txt");

        //gui method
//        ImagePlus warpedImp = tResult.applyTransformationMultiThread(intervals, cx, cy, false);

        //use existing api
//        ImagePlus trainImage = createBWImagePlusUsingExternalMtx(sourceMtx);
//        ImagePlus warpedImp = applyTransformationToImage(tResult, trainImage);

        //new method
        int[][] warpedImageMtx = MiscTools.applyTransformationToGreyscaleImageMtx(tResult, sourceMtx);

        ImagePlus warpedImp = MiscTools.createImagePlusByte(warpedImageMtx, "warped source image");

        FileSaver fs = new FileSaver(warpedImp);
        String tmpImgFile = outputFolder + "/source-image-warped_" + fileName + ".png";
        fs.saveAsPng(tmpImgFile);
    }

    private static void saveMtxAsPng(int[][] inputMtx, String fileName, String folder) {
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

    private static int[][] importMtxFromCsv(String fileName) throws IOException {
        Scanner sc = new Scanner(new File(fileName));

        List<int[]> inputList = new ArrayList<>();
        while (sc.hasNextLine())
        {
            String curLineStr = sc.nextLine();
            String[] curLine = curLineStr.split(",");

            int[] lineValues = Arrays.stream(curLine).map((x) -> Double.parseDouble(x))
                    .mapToInt(d -> d.intValue()).toArray();
            inputList.add(lineValues);
        }
        sc.close();

        int[][] result = inputList.toArray(new int[inputList.size()][]);
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

    public static ImagePlus createBWImagePlusUsingExternalMtx(int[][] aGrayLevelsMtx) {
        //create an ImageProcessor
        ByteProcessor imageProc = new ByteProcessor(aGrayLevelsMtx.length, aGrayLevelsMtx[0].length);

        for (int i=0; i<aGrayLevelsMtx.length; i++) {
            for (int j=0; j<aGrayLevelsMtx[0].length; j++) {
                imageProc.set(j,i,(aGrayLevelsMtx[i][j]));
            }
        }

        return(new ImagePlus("",imageProc));
    }

    private static ImagePlus applyTransformationToImage(Transformation aTransform, ImagePlus aImage) {
        ImagePlus tmpIP = aImage.duplicate();

        BSplineModel source = new BSplineModel(tmpIP.getProcessor(), false, 0);
        source.setPyramidDepth(0);
        source.startPyramids();
        try {
            source.getThread().join();
        } catch (InterruptedException var8) {
            IJ.error("Unexpected interruption exception " + var8);
            return(null);
        }

        int cur_intervals = aTransform.getIntervals();
        double[][] cur_cx = aTransform.getDirectDeformationCoefficientsX();
        double[][] cur_cy = aTransform.getDirectDeformationCoefficientsY();

        MiscTools.applyTransformationToSourceMT(tmpIP, tmpIP, source, cur_intervals, cur_cx, cur_cy);

        return(tmpIP);
    }

    public static Transformation guiRegistration(int[][] sourceMtx, int[][] targetMtx, Param bParams)
    {

        boolean bIsReverse = true;
        if(bParams.mode == MainDialog.MONO_MODE)
        {
            bIsReverse = false;
            bParams.consistencyWeight = 0;
        }

        ImagePlus targetImp = createBWImagePlusUsingExternalMtx(targetMtx);
        ImagePlus sourceImp = createBWImagePlusUsingExternalMtx(sourceMtx);

        // Create image model to perform registration
        BSplineModel source = new BSplineModel(sourceImp.getProcessor(), bIsReverse,
                (int) Math.pow(2, bParams.img_subsamp_fact));

        int imagePyramidDepth = bParams.max_scale_deformation - bParams.min_scale_deformation + 1;
        source.setPyramidDepth(imagePyramidDepth);

        BSplineModel target   = new BSplineModel(targetImp.getProcessor(), true,
                        (int) Math.pow(2, bParams.img_subsamp_fact));

        target.setPyramidDepth(imagePyramidDepth);

        source.startPyramids();
        target.startPyramids();

        // Wait for the pyramids to be done
        try
        {
            source.getThread().join();
            target.getThread().join();
        }
        catch (InterruptedException e)
        {
            IJ.error("Unexpected interruption exception" + e);
        }

        // Create output image (source-target)
        final ImagePlus [] output_ip = new ImagePlus[2];
        output_ip[0] = new ImagePlus("Output Target-Source" );
        output_ip[1] = new ImagePlus("Output Target-Source" );

        final Mask sourceMsk = new Mask(sourceImp.getProcessor(), false);
        final Mask targetMsk = new Mask(sourceImp.getProcessor(), false);

        final double[][] sourceAffineMatrix = null;
        final double[][] targetAffineMatrix = null;

        final ImageProcessor originalSourceIP = sourceImp.getProcessor();
        final ImageProcessor originalTargetIP = targetImp.getProcessor();

        // Prepare registration parameters
        final Transformation warp = new Transformation(
                sourceImp, targetImp, source, target, new PointHandler(sourceImp), new PointHandler(targetImp),
                sourceMsk, targetMsk, sourceAffineMatrix, targetAffineMatrix,
                bParams.min_scale_deformation, bParams.max_scale_deformation,
                0, bParams.divWeight, bParams.curlWeight, bParams.landmarkWeight, bParams.imageWeight,
                bParams.consistencyWeight, bParams.stopThreshold, -1, false, bParams.mode, "", "",
                output_ip[0], output_ip[1], null, originalSourceIP, originalTargetIP);

        if(bParams.mode == MainDialog.MONO_MODE)
        {
            // Do unidirectional registration
            warp.doUnidirectionalRegistration();
        }
        else
        {
            // Do bidirectional registration
            warp.doBidirectionalRegistration();
        }

        return warp;

    }

    private static void saveArrayCSV(double[][] aData, String aFileName) {
        try {
            FileWriter csvWriter = new FileWriter(aFileName);

            for (int i=0; i < aData.length; i++) {
                for (int j=0; j < aData[i].length; j++) {
                    if (j>0) {
                        csvWriter.append(",");
                    }
                    csvWriter.append(String.valueOf(aData[i][j]));

                }
                csvWriter.append("\n");
            }

            csvWriter.flush();
            csvWriter.close();
        } catch (Exception e) {

        }

    }



}

class TestImage {
    int[][] imageMtx;
    String fileName;
    boolean is2D = true;

    public TestImage(int[][] imageMtx, String fileName) {
        this.imageMtx = imageMtx;
        this.fileName = fileName;
    }
}