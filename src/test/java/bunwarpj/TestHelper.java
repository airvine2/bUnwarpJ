package bunwarpj;

import ij.IJ;
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

public class TestHelper {

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
        int intervals = tResult.getIntervals();
        double[][] cx = tResult.getDirectDeformationCoefficientsX();
        double[][] cy = tResult.getDirectDeformationCoefficientsY();
        MiscTools.saveElasticTransformation(intervals, cx, cy, Paths.get(outputFolder, outputFileName + ".txt").toString());
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
            String filePath = Paths.get(aFolder, aFileName).toString();
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
            String filePath = Paths.get(aFolder, aFileName).toString();
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
            String filePath = Paths.get(aFolder, aFileName).toString();
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
            FileWriter csvWriter = new FileWriter(aFilePath);

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

}
