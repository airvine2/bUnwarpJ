package bunwarpj;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHelper {

    public static final String RESOURCES_DIR = "src/test/resources/csv";

    public static final String ONE_DIMENSIONAL_DATA_FILE = "1d-data.csv";

    public static final String ONE_DIMENSIONAL_BIN_COUNTS_FILE = "1d-data-bin-counts.csv";

    public static final String ONE_DIMENSIONAL_BIN_INDEXES_FILE = "1d-data-bin-idx.csv";

    public static final String ONE_DIMENSIONAL_CUMULATIVE_SUM_FILE = "1d-data-bin-counts-cumulative-sum.csv";

    public static final String TWO_DIMENSIONAL_DATA_FILE = "2d-data.csv";

    public static final String TWO_DIMENSIONAL_BIN_COUNTS_FILE = "2d-data-bin-counts.csv";

    public static HashMap<String, int[][]> importAll_CsvToMtxInt(String dataFolder) throws IOException {
        List<String> csvImageFiles = findFilesOfType(dataFolder, ".csv");

        HashMap<String, int[][]> imageMtxList = new HashMap<>();
        for (int i=0; i< csvImageFiles.size(); i++) {
            int[][] curImageMtx = import_CsvToMtxInt(csvImageFiles.get(i));
            imageMtxList.put(csvImageFiles.get(i), curImageMtx);
        }

        return imageMtxList;
    }

    public static HashMap<String, double[][]> importAll_CsvToMtxDouble(String dataFolder) throws IOException {
        List<String> csvImageFiles = findFilesOfType(dataFolder, ".csv");

        HashMap<String, double[][]> imageMtxList = new HashMap<>();
        for (int i=0; i< csvImageFiles.size(); i++) {
            double[][] curImageMtx = import_CsvToMtxDouble(csvImageFiles.get(i));
            imageMtxList.put(csvImageFiles.get(i), curImageMtx);
        }

        return imageMtxList;
    }

    public static int[][] import_CsvToMtxInt(String fileName) throws IOException {
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

    public static double[][] import_CsvToMtxDouble(String fileName) throws IOException {
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

    public static float[][] import_CsvToMtxFloat(String fileName) throws IOException {
        Scanner sc = new Scanner(new File(fileName));

        List<float[]> inputList = new ArrayList<>();
        while (sc.hasNextLine())
        {
            String curLineStr = sc.nextLine();
            String[] curLine = curLineStr.split(",");

            float[] lineValues = new float[curLine.length];
            for (int i = 0 ; i < curLine.length; i++) {
                lineValues[i] = Float.parseFloat(curLine[i]);
            }

            inputList.add(lineValues);
        }
        sc.close();

        float[][] result = inputList.toArray(new float [inputList.size()][]);
        return (result);
    }

    public static List<double[]> import_CsvToList(String fileName) throws IOException {
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

        return (inputList);
    }

    public static int[][] import_PngToMtxInt(String fileName) throws IOException {
        ImagePlus sourceIP = ij.IJ.openImage(fileName);
        int[][] importedMtx = new int[sourceIP.getHeight()][sourceIP.getWidth()];
        MiscTools.extractImage(sourceIP.getProcessor(), importedMtx);
        return(importedMtx);
    }

    public static List<String> findFilesOfType(String folderPath, String fileExtension) throws IOException {

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

    public static void saveMtxAsPng(int[][] inputMtx, String folder, String fileName) {
        ImagePlus mtxAsImp = MiscTools.createImagePlusByte(inputMtx, fileName);
        FileSaver fs = new FileSaver(mtxAsImp);
        String tmpImgFile = Paths.get(folder,fileName + ".png").toString();
        fs.saveAsPng(tmpImgFile);
    }

    /**
     * this is how it's done in the IJ GUI
     * @param tResult
     */
    public static ImagePlus applyTransformationToMtx_gui(Transformation tResult) {
        int intervals = tResult.getIntervals();
        double[][] cx = tResult.getDirectDeformationCoefficientsX();
        double[][] cy = tResult.getDirectDeformationCoefficientsY();
        return tResult.applyTransformationMultiThread(intervals, cx, cy, false);
    }

    /**
     * this is how it was done in the old API
     */
    public static ImagePlus applyTransformationToMtx_oldAPI(Transformation tResult, int[][] sourceMtx) {
        ImagePlus trainImage = createBWImagePlusFromMtx(sourceMtx);
        return MiscTools.applyTransformationToImagePlus(tResult, trainImage);
    }

    /**
     * this is the new method
     */
    public static int[][] applyTransformationToMtx_newAPI(Transformation tResult, int[][] sourceMtx) {
        return MiscTools.applyTransformationToGreyscaleImageMtx(tResult, sourceMtx);
    }

    public static void saveTransformationCoeffs(Transformation tResult, String outputFolder, String outputFileName) {
        double[][] cx = tResult.getDirectDeformationCoefficientsX();
        double[][] cy = tResult.getDirectDeformationCoefficientsY();
        TestHelper.saveArrayCSV(cx, outputFolder, outputFileName + "_X");
        TestHelper.saveArrayCSV(cy, outputFolder, outputFileName + "_Y");
    }

    public static void compareTransformationCoeffsToFile(Transformation tResult, String outputFolder,
                                                         String outputFileName) throws Exception {
        double[][] cx = tResult.getDirectDeformationCoefficientsX();
        double[][] cy = tResult.getDirectDeformationCoefficientsY();

        double[][] expectedcx = TestHelper.import_CsvToMtxDouble(
                Paths.get(outputFolder, outputFileName + "_X.csv").toString());

        if (!Arrays.deepEquals(expectedcx, cx)) {
            String errMsg = "Expected Transformation Mtx X:\n" + mtxToString(expectedcx) +
                    "Actual Transformation Mtx X:\n" + mtxToString(cx);
            assertTrue(false, errMsg);
        }

        double[][] expectedcy = TestHelper.import_CsvToMtxDouble(
                Paths.get(outputFolder, outputFileName + "_Y.csv").toString());
        if (!Arrays.deepEquals(expectedcy, cy)) {
            String errMsg = "Expected Transformation Mtx Y:\n" + mtxToString(expectedcy) +
                    "Actual Transformation Mtx Y:\n" + mtxToString(cy);
            assertTrue(false, errMsg);
        }
    }

    public static ImagePlus createBWImagePlusFromMtx(int[][] aGrayLevelsMtx) {
        //create an ImageProcessor
        ByteProcessor imageProc = new ByteProcessor(aGrayLevelsMtx.length, aGrayLevelsMtx[0].length);

        for (int i=0; i<aGrayLevelsMtx.length; i++) {
            for (int j=0; j<aGrayLevelsMtx[0].length; j++) {
                imageProc.set(j,i,(aGrayLevelsMtx[i][j]));
            }
        }

        return(new ImagePlus("",imageProc));
    }

    public static String getTestResultsPath(String dataFolder) {
        String outputFolder = Paths.get(dataFolder,"results").toString();
        File directory = new File(outputFolder);
        if (!directory.exists()){
            directory.mkdir();
            if (!directory.exists()){
                return null;
            }
        }
        return outputFolder;
    }

    public static void saveArrayCSV(double[][] aData, String aFolder, String aFileName) {
        try {
            String filePath = Paths.get(aFolder, aFileName + ".csv").toString();
            FileWriter csvWriter = new FileWriter(filePath);

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

    public static void saveArrayCSV(int[][] aData, String aFolder, String aFileName) {
        try {
            String filePath = Paths.get(aFolder, aFileName + ".csv").toString();
            FileWriter csvWriter = new FileWriter(filePath);

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

    public static void saveArrayCSV(double[] aData, String aFolder, String aFileName, boolean saveAsColumn) {
        try {
            String filePath = Paths.get(aFolder, aFileName + ".csv").toString();
            FileWriter csvWriter = new FileWriter(filePath);

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

    public static void saveArrayCSV(int[] aData, String aFolder, String aFileName, boolean saveAsColumn) {
        try {
            String filePath = Paths.get(aFolder, aFileName + ".csv").toString();
            FileWriter csvWriter = new FileWriter(filePath);

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

    public static void saveArrayCSV(List<double[]> aData, String aFilePath) {
        try {
            FileWriter csvWriter = new FileWriter(aFilePath + ".csv");

            for (int i=0; i < aData.size(); i++) {
                for (int j=0; j < aData.get(i).length; j++) {
                    if (j>0) {
                        csvWriter.append(",");
                    }
                    csvWriter.append(String.valueOf(aData.get(i)[j]));

                }
                csvWriter.append("\n");
            }

            csvWriter.flush();
            csvWriter.close();
        } catch (Exception e) {
            System.out.println("Could not write csv file: " + e.getMessage());
        }
    }

    public static double[] doubleListToArray(List<Double> inList) {
        return inList.stream().mapToDouble(d -> d).toArray();
    }

    public static <Object> java.lang.Object getStackElement(Stack<Object> stack, int index) {
        if (index == 0) {
            return stack.peek();
        }

        Object x = stack.pop();
        try {
            return getStackElement(stack, index - 1);
        } finally {
            stack.push(x);
        }
    }

    public static boolean compareListOfArrays(List<double[]> list1, List<double[]> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        boolean result = true;
        for (int i=0; i<list1.size(); i++) {
            if (!Arrays.equals(list1.get(i), list2.get(i))) {
                result = false;
                break;
            }
        }

        return result;
    }

    /**
     * Use reflection to access private fields for testing. source:
     * https://stackoverflow.com/questions/34571/how-do-i-test-a-class-that-has-private-methods-fields-or-inner-classes
     * @param object
     * @param fieldName
     * @param value
     * @throws Exception
     */
    public static void setPrivateField(Object object, String fieldName, Object value) throws Exception {
        Class<?> objectClass = object.getClass();
        Field field = objectClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    /**
     * Use reflection to access private fields for testing. source:
     * https://stackoverflow.com/questions/34571/how-do-i-test-a-class-that-has-private-methods-fields-or-inner-classes
     * @param object
     * @param fieldName
     * @throws Exception
     */
    public static Object getPrivateField(Object object, String fieldName) throws Exception {
        Class<?> objectClass = object.getClass();
        Field field = objectClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }

    /**
     * Use reflection to access private methods for testing. source:
     * https://stackoverflow.com/questions/34571/how-do-i-test-a-class-that-has-private-methods-fields-or-inner-classes
     * @param object
     * @param methodName
     * @param argObjects
     * @throws Exception
     */
    public static void callPrivateMethod(Object object, String methodName, Class[] argClasses,
                                         Object... argObjects) throws Exception {
        Class<?> objectClass = object.getClass();
        Method method = objectClass.getDeclaredMethod(methodName, argClasses);
        method.setAccessible(true);
        method.invoke(object, argObjects);
    }

    /**
     * utility function for checking the results of a transformation by
     * comparing coefficients against a file or generating a new file
     */
    public static void compareOrSaveTransform(Transformation warp, String outputFolder, String coeffsFile,
                                        boolean overwriteFile) throws Exception {
        if (overwriteFile) {
            TestHelper.saveTransformationCoeffs(warp, outputFolder, coeffsFile);
        } else {
            TestHelper.compareTransformationCoeffsToFile(warp, outputFolder, coeffsFile);
        }
    }

    /**
     * utility function for checking a matrix by
     * comparing values against a file or generating a new file
     */
    public static void compareOrSaveMtx(int[][] mtx, String outputFolder, String resultsFile,
                                        boolean overwriteFile) throws Exception {
        if (overwriteFile) {
            TestHelper.saveMtxAsPng(mtx, outputFolder, resultsFile);
            TestHelper.saveArrayCSV(mtx, outputFolder, resultsFile);
        } else {
            int[][] expectedMtx = TestHelper.import_CsvToMtxInt(
                    Paths.get(outputFolder, resultsFile + ".csv").toString());
            assertTrue(Arrays.deepEquals(mtx, expectedMtx));
        }
    }

    /**
     * utility function for checking a matrix by
     * comparing values against a file or generating a new file
     */
    public static void compareOrSaveMtx(double[][] mtx, String outputFolder, String resultsFile,
                                        boolean overwriteFile) throws Exception {
        if (overwriteFile) {
            TestHelper.saveArrayCSV(mtx, outputFolder, resultsFile);
        } else {
            double[][] expectedMtx = TestHelper.import_CsvToMtxDouble(
                    Paths.get(outputFolder, resultsFile + ".csv").toString());
            assertTrue(Arrays.deepEquals(mtx, expectedMtx));
        }
    }

    /**
     * utility function for checking an image matrix by
     * comparing values against a file or generating a new file
     */
    public static void compareOrSaveArray(double[] array, String outputFolder, String resultsFile,
                                        boolean overwriteFile) throws Exception {
        if (overwriteFile) {
            TestHelper.saveArrayCSV(array, outputFolder, resultsFile, false);
        } else {
            double[] expectedArray = TestHelper.import_CsvToMtxDouble(
                    Paths.get(outputFolder, resultsFile + ".csv").toString())[0];
            assertTrue(Arrays.equals(array, expectedArray));
        }
    }

    private static String mtxToString(double[][] aMtx) {
        String result = "";
        for (int i=0; i < aMtx.length; i++) {
            for (int j=0; j < aMtx[i].length; j++) {
                if (j>0) {
                    result += ",";
                }
                result += String.valueOf(aMtx[i][j]);

            }
            result += "\n";
        }
        return result;
    }

}
