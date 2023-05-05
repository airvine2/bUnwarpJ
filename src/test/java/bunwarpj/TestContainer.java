package bunwarpj;

import ij.IJ;
import ij.ImagePlus;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * This is a test object which imports data, initializes and holds variables needed for testing bUnwarpj registration
 * Specific for input data source being a 2D int mtx, read in from csv files
 */
public class TestContainer {

    //data input
    //available image types are binary, byte, short, float, color (ImageProcessor subclasses)

    public static final String INT = "int";
    public static final String FLOAT = "float";

    int[][] sourceMtxInt;
    int[][] targetMtxInt;
    float[][] sourceMtxFloat;
    float[][] targetMtxFloat;

    Param options;
    String outputFolder;

    //Bspline modeling and transformation inputs

    BSplineModel source;
    ImagePlus sourceImp;
    Mask sourceMsk;
    PointHandler sourcePh;
    double[][] sourceAffineMatrix = null;

    BSplineModel target;
    ImagePlus targetImp;
    Mask targetMsk;
    PointHandler targetPh;
    double[][] targetAffineMatrix = null;

    int imagePyramidDepth;
    int min_scale_image = 0;
    int outputLevel = -1;
    boolean showMarquardtOptim = false;
    ImagePlus[] output_ip = new ImagePlus[2];

    //transformation calculations

    Transformation warp;


    public TestContainer() throws IOException {
        this.options = new Param(2, 0, 0, 1,
                0.1, 0.1, 0, 1, 10, 0.01);
    }

    public TestContainer(String dataFolder) throws IOException {
        this();
        loadIntCsvData(dataFolder);
    }

    public void loadIntCsvData(String dataFolder) throws IOException {
        loadIntCsvData(dataFolder, "source.csv", "target.csv");
    }

    public void loadIntCsvData(String dataFolder, String sourceFileName, String targetFileName) throws IOException {
        this.sourceMtxInt = TestHelper.import_CsvToMtxInt(Paths.get(dataFolder, sourceFileName).toString());
        this.targetMtxInt = TestHelper.import_CsvToMtxInt(Paths.get(dataFolder, targetFileName).toString());
        this.outputFolder = TestHelper.getTestResultsPath(dataFolder);
    }

    public void loadFloatCsvData(String dataFolder, String dataType, String sourceFileName, String targetFileName) throws IOException {
        this.sourceMtxFloat = TestHelper.import_CsvToMtxFloat(Paths.get(dataFolder, sourceFileName).toString());
        this.targetMtxFloat = TestHelper.import_CsvToMtxFloat(Paths.get(dataFolder, targetFileName).toString());
        this.outputFolder = TestHelper.getTestResultsPath(dataFolder);

        int numRows = this.sourceMtxFloat.length;
        int numCols = this.sourceMtxFloat[0].length;
        this.sourceMtxInt = new int[numRows][numCols];
        this.targetMtxInt = new int[numRows][numCols];

        for (int i=0; i<this.sourceMtxFloat.length; i++) {
            for (int j=0; j<this.sourceMtxFloat[0].length; j++) {
                this.sourceMtxInt[i][j] = (int)this.sourceMtxFloat[i][j];
                this.targetMtxInt[i][j] = (int)this.targetMtxFloat[i][j];
            }
        }

    }

    /**
     * load csv data, where the input values are between 0 and 1. Those values are then rescaled to be between 0-255.
     * the result is copied to both sourceMtxInt and sourceMtxFloat, and the same for targetMtx
     * @param dataFolder
     * @param sourceFileName
     * @param targetFileName
     * @throws IOException
     */
    public void loadUnscaledFloatCsvData(String dataFolder, String sourceFileName, String targetFileName) throws IOException {
        this.sourceMtxFloat = TestHelper.import_CsvToMtxFloat(Paths.get(dataFolder, sourceFileName).toString());
        this.targetMtxFloat = TestHelper.import_CsvToMtxFloat(Paths.get(dataFolder, targetFileName).toString());

        int numRows = this.sourceMtxFloat.length;
        int numCols = this.sourceMtxFloat[0].length;
        this.sourceMtxInt = new int[numRows][numCols];
        this.targetMtxInt = new int[numRows][numCols];

        for (int i=0; i<this.sourceMtxFloat.length; i++) {
            for (int j=0; j<this.sourceMtxFloat[0].length; j++) {
                this.sourceMtxFloat[i][j] = this.sourceMtxFloat[i][j] * 255f;
                this.targetMtxFloat[i][j] = this.targetMtxFloat[i][j] * 255f;
                this.sourceMtxInt[i][j] = (int)this.sourceMtxFloat[i][j];
                this.targetMtxInt[i][j] = (int)this.targetMtxFloat[i][j];
            }
        }

        this.outputFolder = TestHelper.getTestResultsPath(dataFolder);
    }

    public void initializeTransformationInputs_Int() {
        // Create source image model
        this.source = new BSplineModel(this.sourceMtxInt, true);
        this.sourceImp = MiscTools.createImagePlusByte(this.sourceMtxInt, "source image");
        sourceMsk = new Mask(this.sourceMtxInt[0].length, this.sourceMtxInt.length);
        sourcePh  = new PointHandler(sourceImp);

        // Create target image model
        this.target = new BSplineModel(this.targetMtxInt, true);
        this.targetImp = MiscTools.createImagePlusByte(this.targetMtxInt, "target image");
        targetMsk = new Mask(this.targetMtxInt[0].length, this.targetMtxInt.length);
        targetPh = new PointHandler(targetImp);

        // Produce side information
        this.imagePyramidDepth = this.options.max_scale_deformation - this.options.min_scale_deformation + 1;
    }

    public void initializeTransformationInputs_Float() {


        // Create source image model
        this.source = new BSplineModel(this.sourceMtxFloat, true);
        this.sourceImp = MiscTools.createImagePlusFloat(this.sourceMtxFloat, "source image");
        sourceMsk = new Mask(this.sourceMtxFloat[0].length, this.sourceMtxFloat.length);
        sourcePh  = new PointHandler(sourceImp);

        // Create target image model
        this.target = new BSplineModel(this.targetMtxFloat, true);
        this.targetImp = MiscTools.createImagePlusFloat(this.targetMtxFloat, "target image");
        targetMsk = new Mask(this.targetMtxFloat[0].length, this.targetMtxFloat.length);
        targetPh = new PointHandler(targetImp);

        // Produce side information
        this.imagePyramidDepth = this.options.max_scale_deformation - this.options.min_scale_deformation + 1;
    }

    public void buildBSplineModels() {
        //calculate the BSpline model coefficients for source and target
        target.setPyramidDepth(imagePyramidDepth+min_scale_image);
        target.startPyramids();

        source.setPyramidDepth(imagePyramidDepth + min_scale_image);
        source.startPyramids();

        try
        {
            source.getThread().join();
            target.getThread().join();
        }
        catch (InterruptedException e)
        {
            IJ.log("Unexpected interruption exception " + e);
        }
    }

    public void initializeTransformationObject() {
        this.warp = new Transformation(
                this.sourceImp, this.targetImp, source, target, this.sourcePh, this.targetPh,
                this.sourceMsk, this.targetMsk, this.sourceAffineMatrix, this.targetAffineMatrix,
                this.options.min_scale_deformation, this.options.max_scale_deformation,
                min_scale_image, this.options.divWeight,
                this.options.curlWeight, this.options.landmarkWeight, this.options.imageWeight,
                this.options.consistencyWeight, this.options.stopThreshold,
                this.outputLevel, this.showMarquardtOptim, this.options.mode,null, null,
                this.output_ip[0], this.output_ip[1], null,
                this.sourceImp.getProcessor(), this.targetImp.getProcessor());

        // Initial affine transform correction values
        if ((this.sourceAffineMatrix == null) && (this.targetAffineMatrix == null)) {
            warp.setAnisotropyCorrection(this.options.getAnisotropyCorrection());
            warp.setScaleCorrection(this.options.getScaleCorrection());
            warp.setShearCorrection(this.options.getShearCorrection());
        }
    }





}
