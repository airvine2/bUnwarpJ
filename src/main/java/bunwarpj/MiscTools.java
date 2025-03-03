/*-
 * #%L
 * bUnwarpJ plugin for Fiji.
 * %%
 * Copyright (C) 2005 - 2020 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package bunwarpj;

/**
 * bUnwarpJ plugin for ImageJ(C).
 * Copyright (C) 2005-2010 Ignacio Arganda-Carreras and Jan Kybic 
 *
 * More information at http://biocomp.cnb.csic.es/%7Eiarganda/bUnwarpJ/
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JFileChooser;

/**
 * Different tools for the bUnwarpJ interface.
 */
public class MiscTools
{
	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation to the source (gray-scale) image.
	 * The source image is modified. The target image is used to know
	 * the output size.
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param source source image model
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 * 
	 * @deprecated
	 */
	static public void applyTransformationToSource(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			BSplineModel source,
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		int targetHeight = targetImp.getProcessor().getHeight();
		int targetWidth  = targetImp.getProcessor().getWidth();
		int sourceHeight = sourceImp.getProcessor().getHeight();
		int sourceWidth  = sourceImp.getProcessor().getWidth();

		// Ask for memory for the transformation
		double [][] transformation_x = new double [targetHeight][targetWidth];
		double [][] transformation_y = new double [targetHeight][targetWidth];

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx = new BSplineModel(cx);
		BSplineModel swy = new BSplineModel(cy);

		// Compute the transformation mapping
		boolean ORIGINAL = false;
		for (int v=0; v<targetHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetHeight > 1) ? ((double)(v * intervals) / (double)(targetHeight - 1) + 1.0F) : 1.0F;
			for (int u = 0; u<targetWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetWidth > 1) ? ((double)(u * intervals) / (double)(targetWidth - 1) + 1.0F) : 1.0F;

				swx.prepareForInterpolation(tu, tv, ORIGINAL);
				transformation_x[v][u] = swx.interpolateI();

				swy.prepareForInterpolation(tu, tv, ORIGINAL);
				transformation_y[v][u] = swy.interpolateI();
			}
		}

		// Compute the warped image
		/* GRAY SCALE IMAGES */
		if(!(sourceImp.getProcessor() instanceof ColorProcessor))
		{
			source.startPyramids();
			try{
				source.getThread().join();
			} catch (InterruptedException e) {
				IJ.error("Unexpected interruption exception " + e);
			}
			
			FloatProcessor fp = new FloatProcessor(targetWidth, targetHeight);
			for (int v=0; v<targetHeight; v++)
				for (int u=0; u<targetWidth; u++)
				{
					final double x = transformation_x[v][u];
					final double y = transformation_y[v][u];

					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{
						source.prepareForInterpolation(x, y, ORIGINAL);
						fp.setf(u, v, (float) source.interpolateI());
					}
					else
						fp.setf(u, v, 0f);
				}
			fp.resetMinAndMax();
			sourceImp.setProcessor(sourceImp.getTitle(), fp);
			sourceImp.updateImage();
		}
		else /* COLOR IMAGES */
		{        	
			// red
			BSplineModel sourceR = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(0, null), false, 1);
			sourceR.setPyramidDepth(0);
			sourceR.startPyramids();
			// green
			BSplineModel sourceG = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(1, null), false, 1);
			sourceG.setPyramidDepth(0);
			sourceG.startPyramids();
			//blue
			BSplineModel sourceB = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(2, null), false, 1);
			sourceB.setPyramidDepth(0);
			sourceB.startPyramids();

			// Join threads
			try {
				sourceR.getThread().join();
				sourceG.getThread().join();
				sourceB.getThread().join();
			} catch (InterruptedException e) {
				IJ.error("Unexpected interruption exception " + e);
			}

			// Calculate warped RGB image
			ColorProcessor cp = new ColorProcessor(targetWidth, targetHeight);
			FloatProcessor fpR = new FloatProcessor(targetWidth, targetHeight);
			FloatProcessor fpG = new FloatProcessor(targetWidth, targetHeight);
			FloatProcessor fpB = new FloatProcessor(targetWidth, targetHeight);
			for (int v=0; v<targetHeight; v++)
				for (int u=0; u<targetWidth; u++)
				{
					final double x = transformation_x[v][u];
					final double y = transformation_y[v][u];

					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{                	 
						sourceR.prepareForInterpolation(x, y, ORIGINAL);
						fpR.setf(u, v, (float) sourceR.interpolateI());

						sourceG.prepareForInterpolation(x, y, ORIGINAL);
						fpG.setf(u, v, (float) sourceG.interpolateI());

						sourceB.prepareForInterpolation(x, y, ORIGINAL);
						fpB.setf(u, v, (float) sourceB.interpolateI());                 
					}
					else
					{
						fpR.setf(u, v, 0f);
						fpG.setf(u, v, 0f);
						fpB.setf(u, v, 0f);
					}
				}
			cp.setPixels(0, fpR);
			cp.setPixels(1, fpG);
			cp.setPixels(2, fpB);            
			cp.resetMinAndMax();

			sourceImp.setProcessor(sourceImp.getTitle(), cp);
			sourceImp.updateImage();
		}
	}

	/**
	 * Apply a given splines transformation to the source (RGB color) image.
	 * The source image is modified. The target image is used to know
	 * the output size.
	 * 
	 * @deprecated
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param sourceR image model of the source red channel 
	 * @param sourceG image model of the source green channel
	 * @param sourceB image model of the source blue channel
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 */
	static public void applyTransformationToSource(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			BSplineModel sourceR,
			BSplineModel sourceG,
			BSplineModel sourceB,
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		int targetHeight = targetImp.getProcessor().getHeight();
		int targetWidth  = targetImp.getProcessor().getWidth ();
		int sourceHeight = sourceImp.getProcessor().getHeight();
		int sourceWidth  = sourceImp.getProcessor().getWidth ();

		// Ask for memory for the transformation
		double [][] transformation_x = new double [targetHeight][targetWidth];
		double [][] transformation_y = new double [targetHeight][targetWidth];

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx = new BSplineModel(cx);
		BSplineModel swy = new BSplineModel(cy);

		// Compute the transformation mapping
		boolean ORIGINAL = false;
		for (int v=0; v<targetHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetHeight > 1) ? ((double)(v * intervals) / (double)(targetHeight - 1) + 1.0F) : 1.0F;
			for (int u = 0; u<targetWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetWidth > 1) ? ((double)(u * intervals) / (double)(targetWidth - 1) + 1.0F) : 1.0F;

				swx.prepareForInterpolation(tu, tv, ORIGINAL);
				transformation_x[v][u] = swx.interpolateI();

				swy.prepareForInterpolation(tu, tv, ORIGINAL);
				transformation_y[v][u] = swy.interpolateI();
			}
		}

		// Compute the warped image
		ColorProcessor cp = new ColorProcessor(targetWidth, targetHeight);
		FloatProcessor fpR = new FloatProcessor(targetWidth, targetHeight);
		FloatProcessor fpG = new FloatProcessor(targetWidth, targetHeight);
		FloatProcessor fpB = new FloatProcessor(targetWidth, targetHeight);
		for (int v=0; v<targetHeight; v++)
			for (int u=0; u<targetWidth; u++)
			{
				final double x = transformation_x[v][u];
				final double y = transformation_y[v][u];

				if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
				{                	 
					sourceR.prepareForInterpolation(x, y, ORIGINAL);
					fpR.setf(u, v, (float) sourceR.interpolateI());

					sourceG.prepareForInterpolation(x, y, ORIGINAL);
					fpG.setf(u, v, (float) sourceG.interpolateI());

					sourceB.prepareForInterpolation(x, y, ORIGINAL);
					fpB.setf(u, v, (float) sourceB.interpolateI());                 
				}
				else
				{
					fpR.setf(u, v, 0f);
					fpG.setf(u, v, 0f);
					fpB.setf(u, v, 0f);
				}
			}
		cp.setPixels(0, fpR);
		cp.setPixels(1, fpG);
		cp.setPixels(2, fpB);            
		cp.resetMinAndMax();

		sourceImp.setProcessor(sourceImp.getTitle(), cp);
		sourceImp.updateImage();
	}    

	/**
	 * Apply a given raw transformation to the source image.
	 * The source image is modified. The target image is used to know
	 * the output size.
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param source source image
	 * @param transformation_x x- mapping coordinates
	 * @param transformation_y y- mapping coordinates
	 */
	static public void applyRawTransformationToSource(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			BSplineModel source,
			double [][] transformation_x,
			double [][] transformation_y)
	{
		int targetHeight = targetImp.getProcessor().getHeight();
		int targetWidth  = targetImp.getProcessor().getWidth ();
		int sourceHeight = sourceImp.getProcessor().getHeight();
		int sourceWidth  = sourceImp.getProcessor().getWidth ();

		boolean ORIGINAL = false;

		// Compute the warped image
		/* GRAY SCALE IMAGES */
		if(!(sourceImp.getProcessor() instanceof ColorProcessor))
		{
			// Start source
			source.startPyramids();
			
			// Join threads
			try {
				source.getThread().join();
			} catch (InterruptedException e) {
				IJ.error("Unexpected interruption exception " + e);
			}
			
			FloatProcessor fp = new FloatProcessor(targetWidth, targetHeight);
			for (int v=0; v<targetHeight; v++)
				for (int u=0; u<targetWidth; u++)
				{
					final double x = transformation_x[v][u];
					final double y = transformation_y[v][u];

					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{
						source.prepareForInterpolation(x, y, ORIGINAL);
						fp.setf(u, v, (float) source.interpolateI());
					}
					else
						fp.setf(u, v, 0f);
				}
			fp.resetMinAndMax();
			sourceImp.setProcessor(sourceImp.getTitle(), fp);
			sourceImp.updateImage();
		}
		else /* COLOR IMAGES */
		{        	
			// red
			BSplineModel sourceR = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(0, null), false, 1);
			sourceR.setPyramidDepth(0);
			//sourceR.getThread().start();
			sourceR.startPyramids();
			
			// green
			BSplineModel sourceG = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(1, null), false, 1);
			sourceG.setPyramidDepth(0);
			//sourceG.getThread().start();
			sourceG.startPyramids();
			
			//blue
			BSplineModel sourceB = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(2, null), false, 1);
			sourceB.setPyramidDepth(0);
			//sourceB.getThread().start();
			sourceB.startPyramids();

			// Join threads
			try {
				sourceR.getThread().join();
				sourceG.getThread().join();
				sourceB.getThread().join();
			} catch (InterruptedException e) {
				IJ.error("Unexpected interruption exception " + e);
			}

			// Calculate warped RGB image
			ColorProcessor cp = new ColorProcessor(targetWidth, targetHeight);
			FloatProcessor fpR = new FloatProcessor(targetWidth, targetHeight);
			FloatProcessor fpG = new FloatProcessor(targetWidth, targetHeight);
			FloatProcessor fpB = new FloatProcessor(targetWidth, targetHeight);
			for (int v=0; v<targetHeight; v++)
				for (int u=0; u<targetWidth; u++)
				{
					final double x = transformation_x[v][u];
					final double y = transformation_y[v][u];

					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{                	 
						sourceR.prepareForInterpolation(x, y, ORIGINAL);
						fpR.setf(u, v, (float) sourceR.interpolateI());

						sourceG.prepareForInterpolation(x, y, ORIGINAL);
						fpG.setf(u, v, (float) sourceG.interpolateI());

						sourceB.prepareForInterpolation(x, y, ORIGINAL);
						fpB.setf(u, v, (float) sourceB.interpolateI());                 
					}
					else
					{
						fpR.setf(u, v, 0f);
						fpG.setf(u, v, 0f);
						fpB.setf(u, v, 0f);
					}
				}
			cp.setPixels(0, fpR);
			cp.setPixels(1, fpG);
			cp.setPixels(2, fpB);            
			cp.resetMinAndMax();

			sourceImp.setProcessor(sourceImp.getTitle(), cp);
			sourceImp.updateImage();
		} // end calculating warped color image

	}
	/**
	 * Approximate the transformed coordinates of a point from the
	 * moving image into the fixed image. Notice the raw transform
	 * stores for each fixed image position the coordinates of the
	 * corresponding moving pixel to fill it in. Therefore the new
	 * position of the moving (source) point in fixed (target) space
	 * can be approximated by finding the closest point in the
	 * transform.
	 *
	 * @param movingCoords point coordinates in moving space
	 * @param fixedImp fixed (source) image
	 * @param transformation_x raw transform x-coordinates
	 * @param transformation_y raw transform y-coordinates
	 * @return approximated position of the point after transformation
	 */
	public static double[] approximateInverseCoords(
		double[] movingCoords,
		ImagePlus fixedImp,
		double [][] transformation_x,
		double [][] transformation_y
		)
	{
	    double[] coords = new double[2];
	    double minDistance = Double.MAX_VALUE;
	    for (int v=0; v<fixedImp.getHeight(); v++)
		for (int u=0; u<fixedImp.getWidth(); u++)
		{
		    final double x = transformation_x[v][u];
		    final double y = transformation_y[v][u];
		    final double xdiff = x-movingCoords[0];
		    final double ydiff = y-movingCoords[1];
		    final double dist = Math.sqrt(xdiff*xdiff+ydiff*ydiff);
		    if( dist < minDistance )
		    {
			minDistance = dist;
			coords[0] = u;
			coords[1] = v;
		    }
		}
	    return coords;
	}
	/**
	 * Calculate the warping index between two opposite elastic deformations.
	 * Note: the only difference between the warping index and the consistency
	 * term formulae is a squared root: warping index = sqrt(consistency error)
	 * @param directTransfPath complete path to direct elastic transform
	 * @param inverseTransfPath complete path to inverse elastic transform
	 * @param targetImp target image
	 * @param sourceImp source image
	 * @return geometric error (warping index) between both deformations
	 */
	public static double oppositeWarpingIndex(
			final String directTransfPath,
			final String inverseTransfPath,
			final ImagePlus targetImp,
			final ImagePlus sourceImp)
	{
		// read number of intervals from direct transform file
    	int intervals =
    			MiscTools.numberOfIntervalsOfTransformation( directTransfPath );
    	// create variables to store coefficients
		double [][]cx_direct = new double[ intervals+3 ][ intervals+3 ];
		double [][]cy_direct = new double[ intervals+3 ][ intervals+3 ];
		// read coefficients from file
		MiscTools.loadTransformation( directTransfPath, cx_direct, cy_direct );

		// read number of intervals from inverse transform file
		intervals =
			MiscTools.numberOfIntervalsOfTransformation( inverseTransfPath );
		// create variables to store coefficients
		double [][]cx_inverse = new double[intervals+3][intervals+3];
		double [][]cy_inverse = new double[intervals+3][intervals+3];
		// read coefficients from file
		MiscTools.loadTransformation(
				inverseTransfPath, cx_inverse, cy_inverse );

		// Now we compare both transformations through the "warping index",
		// which is a method equivalent to our consistency measure.
		return MiscTools.warpingIndex( sourceImp, targetImp, intervals,
				cx_direct, cy_direct, cx_inverse, cy_inverse );
	}
	/**
	 * Calculate the warping index between two opposite elastic deformations.
	 * Note: the only difference between the warping index and the consistency 
	 * term formulae is a squared root: warping index = sqrt(consistency error).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx_direct direct transformation x- B-spline coefficients
	 * @param cy_direct direct transformation y- B-spline coefficients
	 * @param cx_inverse inverse transformation x- B-spline coefficients
	 * @param cy_inverse inverse transformation y- B-spline coefficients
	 * 
	 * @return geometric error (warping index) between both deformations.
	 */
	public static double warpingIndex(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			int intervals,
			double [][]cx_direct,
			double [][]cy_direct,
			double [][]cx_inverse,
			double [][]cy_inverse)
	{
		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();
		int sourceCurrentHeight = sourceImp.getProcessor().getHeight();
		int sourceCurrentWidth  = sourceImp.getProcessor().getWidth ();

		double [][] transformation_x_direct = new double [targetCurrentHeight][targetCurrentWidth];
		double [][] transformation_y_direct = new double [targetCurrentHeight][targetCurrentWidth];

		double [][] transformation_x_inverse = new double [sourceCurrentHeight][sourceCurrentWidth];
		double [][] transformation_y_inverse = new double [sourceCurrentHeight][sourceCurrentWidth];

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;

		// We pass the coefficients to a one-dimension array
		// Direct coefficients.
		double c_direct[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c_direct[n     ] = cx_direct[i][j];
				c_direct[n + Nk] = cy_direct[i][j];
			}

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx_direct = new BSplineModel(c_direct, cYdim, cXdim, 0);
		BSplineModel swy_direct = new BSplineModel(c_direct, cYdim, cXdim, Nk);

		// Inverse coefficients.
		double c_inverse[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c_inverse[n     ] = cx_inverse[i][j];
				c_inverse[n + Nk] = cy_inverse[i][j];
			}

		BSplineModel swx_inverse = new BSplineModel(c_inverse, cYdim, cXdim, 0);
		BSplineModel swy_inverse = new BSplineModel(c_inverse, cYdim, cXdim, Nk);

		// Compute the direct transformation mapping
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;
			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx_direct.prepareForInterpolation(tu, tv, false);
				transformation_x_direct[v][u] = swx_direct.interpolateI();

				swy_direct.prepareForInterpolation(tu, tv, false);
				transformation_y_direct[v][u] = swy_direct.interpolateI();
			}
		}

		// Compute the inverse transformation mapping
		for (int v=0; v<sourceCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (sourceCurrentHeight > 1) ? ((double)(v * intervals) / (double)(sourceCurrentHeight - 1) + 1.0F) : 1.0F;
			for (int u = 0; u<sourceCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (sourceCurrentWidth > 1) ? ((double)(u * intervals) / (double)(sourceCurrentWidth - 1) + 1.0F) : 1.0F;

				swx_inverse.prepareForInterpolation(tu, tv, false);
				transformation_x_inverse[v][u] = swx_inverse.interpolateI();

				swy_inverse.prepareForInterpolation(tu, tv, false);
				transformation_y_inverse[v][u] = swy_inverse.interpolateI();
			}
		}


		// *********** Compute the geometric error ***********
		double warpingIndex = 0;
		int n = 0;
		for (int v=0; v<targetCurrentHeight; v++)
			for (int u=0; u<targetCurrentWidth; u++)
			{
				// Check if this point is in the target mask

				final int x = (int) Math.round(transformation_x_direct[v][u]);
				final int y = (int) Math.round(transformation_y_direct[v][u]);

				if (x>=0 && x<sourceCurrentWidth && y>=0 && y<sourceCurrentHeight)
				{
					final double x2 = transformation_x_inverse[y][x];
					final double y2 = transformation_y_inverse[y][x];
					double aux1 = u - x2;
					double aux2 = v - y2;

					warpingIndex += aux1 * aux1 + aux2 * aux2;

					n++; // Another point has been successfully evaluated
				}
			}

		if(n != 0)
		{
			warpingIndex /= (double) n;
			// Note: the only difference between the warping index and the 
			// consistency term is this squared root.
			warpingIndex = Math.sqrt(warpingIndex);            
		}
		else
			warpingIndex = -1;
		return warpingIndex;
	}
	/**
	 * Save elastic transform as raw transform.
	 * @param elasticTransfPath complete path to elastic transform
	 * @param rawTransfPath complete path to raw transform
	 * @param targetImp target image
	 */
	public static void saveElasticAsRaw(
			String elasticTransfPath,
			String rawTransfPath,
			ImagePlus targetImp )
	{
		int intervals = MiscTools.numberOfIntervalsOfTransformation(
				elasticTransfPath );

		double [][]cx = new double[intervals+3][intervals+3];
		double [][]cy = new double[intervals+3][intervals+3];

		MiscTools.loadTransformation(elasticTransfPath, cx, cy);

		// We calculate the transformation raw table.
		double[][] transformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		MiscTools.convertElasticTransformationToRaw( targetImp, intervals, cx,
				cy, transformation_x, transformation_y );

		MiscTools.saveRawTransformation(rawTransfPath, targetImp.getWidth(),
				targetImp.getHeight(), transformation_x, transformation_y );
	}
	/**
	 * Save raw transform as elastic.
	 * @param rawTransfPath complete path to input raw transform
	 * @param elasticTransfPath complete path to output elastic transform
	 * @param intervals number of intervals between B-spline coefficients
	 * @param targetImp target image
	 */
	public static void saveRawAsElastic(
			String rawTransfPath,
			String elasticTransfPath,
			int intervals,
			ImagePlus targetImp )
	{
		double[][] transformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		MiscTools.loadRawTransformation( rawTransfPath, transformation_x,
				transformation_y );

		// We calculate the B-spline transformation coefficients.
		double [][]cx = new double[ intervals + 3 ][ intervals + 3 ];
		double [][]cy = new double[ intervals + 3 ][ intervals + 3 ];

		MiscTools.convertRawTransformationToBSpline( targetImp, intervals,
				transformation_x, transformation_y, cx, cy );

		MiscTools.saveElasticTransformation( intervals, cx, cy,
				elasticTransfPath );
	}

	//------------------------------------------------------------------
	/**
	 * Calculate the raw transformation mapping from B-spline
	 * coefficients.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx transformation x- B-spline coefficients
	 * @param cy transformation y- B-spline coefficients
	 * @param transformation_x raw transformation in x- axis (output)
	 * @param transformation_y raw transformation in y- axis (output)
	 */
	public static void convertElasticTransformationToRaw(
			ImagePlus targetImp,
			int intervals,
			double [][] cx,
			double [][] cy,
			double [][] transformation_x,
			double [][] transformation_y)
	{

		if(cx == null || cy == null || transformation_x == null || transformation_y == null)
		{
			IJ.error("Error in transformations parameters!");
			return;
		}

		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;

		// We pass the coefficients to a one-dimension array
		// Direct coefficients.
		double c[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c[n     ] = cx[i][j];
				c[n + Nk] = cy[i][j];
			}

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx = new BSplineModel(c, cYdim, cXdim, 0);
		BSplineModel swy = new BSplineModel(c, cYdim, cXdim, Nk);


		swx.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);


		// Compute the direct transformation mapping
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;

			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx.prepareForInterpolation(tu, tv, false);
				transformation_x[v][u] = swx.interpolateI();

				swy.prepareForInterpolation(tu, tv, false);
				transformation_y[v][u] = swy.interpolateI();
			}
		}
	} // end convertElasticTransformationToRaw 



	
	//------------------------------------------------------------------
	/**
	 * Convert the raw transformation mapping to B-spline
	 * coefficients.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param transformation_x raw transformation in x- axis 
	 * @param transformation_y raw transformation in y- axis 
	 * @param cx transformation in x- by B-spline coefficients (output)
	 * @param cy transformation in y- by B-spline coefficients (output)	 
	 */
	/*
	public static void convertRawTransformationToBSpline(
			final ImagePlus targetImp,
			final int intervals,
			final double [] transformation_x,
			final double [] transformation_y,
			final double [][] cx,
			final double [][] cy)
	{

		if(cx == null || cy == null || transformation_x == null || transformation_y == null)
		{
			IJ.error("Error in transformations parameters!");
			return;
		}
		
		
		// Number of B-spline coefficients in every direction
		int n_bsplines = intervals + 3;
		// Original image size
		int width = targetImp.getWidth();
		int height = targetImp.getHeight();
		
		// We scale first the transformations to have size
		// n_bsplines x n_bsplines		
		//double xScale = (double) n_bsplines / width;
		//double yScale = (double) n_bsplines / height;
				
		
		// Create float processors with the transformation tables
		IJ.log("original transformation, size = " + width +"x" + height);
		
		FloatProcessor fpX 
			= new FloatProcessor(width, height, transformation_x);
		//ImagePlus impX = new ImagePlus("x_original" , fpX.duplicate() );
		//ImagePlus imp = new ImagePlus("x_original size" , fpX );
		//imp.show();
		//impX.show();
		
		FloatProcessor fpY
			= new FloatProcessor(width, height, transformation_y);
		
		// Set interpolation method to bilinear
		fpX.setInterpolationMethod(ImageProcessor.NONE);
		fpY.setInterpolationMethod(ImageProcessor.NONE);
		
		//int b_width  = n_bsplines-2; 
        //int b_height = n_bsplines-2;
		
		// Scale them to the desired dimensions
		
//		double s = 0.5 * width / (n_bsplines-2);
//		IJ.run(impX, "Gaussian Blur...", "sigma=" + Math.sqrt( s * s - 0.25 ) + " stack" );
//		IJ.run(impX, "Scale...", "x=- y=- width=" + b_width + " height=" + b_height + " process title=- interpolation=None" );
//		
//		int extraX =0; // (width % 2 == 0) ? 1 : 0;
//        int extraY =0; // (height % 2 == 0) ? 1 : 0;
//        
//        
//        IJ.log(" (b_width % 2 == 0) = " + (b_width % 2 == 0));
//        IJ.log(" width / 2 = " + (width / 2));
//        IJ.log(" b_width/2 = " + (b_width/2));
//        IJ.log(" extraX = " + extraX);
//        
//        
//        int initialX = (b_width % 2 == 0) ? (width / 2 - b_width/2 + extraX) : (width / 2 - b_width/2 +1 -extraX);
//        int initialY = (b_height % 2 == 0) ? (height / 2 - b_height/2 + extraY) : (height / 2 - b_height/2 +1 -extraY);
//		
//        impX.setRoi(initialX, initialY, b_width, b_height);
//        IJ.log(" " + initialX + " " +  initialY + " " +  b_width + " " +  b_height);
//        
//        
//        //ImagePlus cop = new ImagePlus("uncropped", impX.getProcessor().duplicate());
//        //cop.setRoi(initialX, initialY, b_width, b_height);
//        //cop.show();
//                
//        IJ.run(impX, "Crop", "");
        
		
		//FloatProcessor x_samples = (FloatProcessor) impX.getProcessor();
		
		
		//IJ.log("Resize to " + b_width + "x" + b_height + " n_bsplines = " + n_bsplines);
		
		//FloatProcessor y_samples = (FloatProcessor) fpY.resize(n_bsplines-2, n_bsplines-2);
//		//ImagePlus impY = new ImagePlus("y_original" , fpY );
//		
//		
//		//IJ.run(impY, "Gaussian Blur...", "sigma=" + Math.sqrt( s * s - 0.25 ) + " stack" );
//		IJ.run(impY, "Scale...", "x=- y=- width=" + (n_bsplines-2) + " height=" + (n_bsplines-2) + " process title=- interpolation=None" );
//		//IJ.run(impY, "Canvas Size...", "width=" + (n_bsplines-2) + " height=" + (n_bsplines-2) + " position=Center" );
//		impY.setRoi(initialX, initialY, b_width, b_height);
//		
//        IJ.run(impY, "Crop", "");
        
		
		//FloatProcessor y_samples = (FloatProcessor) impY.getProcessor();
		
		
		// Padding
			
//		float[] x_pad_array   
//			= MathTools.antiSymmetricPadding((float[]) x_samples.getPixels(), n_bsplines-2, 1);
//		FloatProcessor x_padd_samp = new FloatProcessor(n_bsplines, n_bsplines, x_pad_array, fpX.getColorModel());
//		
//		(new ImagePlus("x_padd_samp" , x_padd_samp )).show();
//		
//		float[] y_pad_array 
//			= MathTools.antiSymmetricPadding((float[]) y_samples.getPixels(), n_bsplines-2, 1);
//		FloatProcessor y_padd_samp = new FloatProcessor(n_bsplines, n_bsplines, y_pad_array, fpY.getColorModel());
		
		
		
	//	FloatProcessor old_fpX = (FloatProcessor)fpX.duplicate();
	//	FloatProcessor old_fpT = (FloatProcessor)fpY.duplicate();
		
//		FloatProcessor reduced_fpX = (FloatProcessor) fpX.resize(n_bsplines, n_bsplines);
//		
//		(new ImagePlus("X resized", reduced_fpX)).show();
//		
//		FloatProcessor reduced_fpY = (FloatProcessor) fpY.resize(n_bsplines, n_bsplines);
//			
//		BSplineModel xModel = new BSplineModel(reduced_fpX, false, 1);
//		xModel.startPyramids();
//		
//		BSplineModel yModel = new BSplineModel(reduced_fpY, false, 1);
//		yModel.startPyramids();
//		
//		// Join threads
//		try {
//			xModel.getThread().join();
//			yModel.getThread().join();
//
//		} catch (InterruptedException e) {
//			IJ.error("Unexpected interruption exception " + e);
//		}
		//double x_factor = Math.sqrt(2);
		//double y_factor = 1.0 / Math.sqrt(2);
		
		//IJ.log("x_factor = " + x_factor + " y_factor = " + y_factor);
		
		//System.out.println("-----");
//		for(int i = 0; i < n_bsplines; i ++)
//		{
//			for(int j = 0; j < n_bsplines; j ++)
//			{
//				cx[i][j] = xModel.getCoefficients()[j + n_bsplines * i];
//				//System.out.print(" " + xModel.getCoefficients()[j + n_bsplines * i]);
//				cy[i][j] = yModel.getCoefficients()[j + n_bsplines * i];
//			}
//			//System.out.println(" ");
//		}
//		//System.out.println("-----");
//		
//		// Produce reduce coeffs to compare
//		
//		
//		
//		
//		
//		double c[] = xModel.getCoefficients();
//		double halfc [] = xModel.reduceCoeffsBy2(c, n_bsplines, n_bsplines);		
//		
//		StringBuilder sb = new StringBuilder();
//		sb.append("------- REDUCED X-----\n");
//		int half_n = n_bsplines/2;
//		for(int i = 0; i < half_n; i ++)
//		{
//			for(int j = 0; j < half_n; j ++)
//			{				
//				sb.append(" " + halfc[j + half_n * i]);				
//			}
//			sb.append(" \n");
//		}
//		sb.append("\n-----\n");
//		
//		IJ.log(sb.toString());
//
//	} // end convertRawTransformationToBSpline 	
*/
	
	
	
	/*------------------------------------------------------------------
	/**
	 * Convert the raw transformation mapping to B-spline
	 * coefficients.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param transformation_x raw transformation in x- axis 
	 * @param transformation_y raw transformation in y- axis 
	 * @param cx transformation x- B-spline coefficients (output)
	 * @param cy transformation y- B-spline coefficients (output)	 
	 */
	public static void convertRawTransformationToBSpline(
			ImagePlus targetImp,
			int intervals,
			double [][] transformation_x,
			double [][] transformation_y,
			double [][] cx,
			double [][] cy)
	{

		if(cx == null || cy == null || transformation_x == null || transformation_y == null)
		{
			IJ.error("Error in transformations parameters!");
			return;
		}

		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();
				
		// Incorporate the raw transformation into the spline coefficient matrix
		for (int i= 0; i<intervals + 3; i++)
		{
			final double v = (double)((i - 1) * (targetCurrentHeight - 1)) / (double)intervals;
			for (int j = 0; j < intervals + 3; j++)
			{
				final double u = (double)((j - 1) * (targetCurrentWidth - 1)) / (double)intervals;
				if(v >= 0 && v < targetCurrentHeight && u < targetCurrentWidth && u >= 0)
				{
					final int tv = (int) v;
					final int tu = (int) u;
					cx[i][j] = transformation_x[tv][tu];
					cy[i][j] = transformation_y[tv][tu];
				}
			}
		}
		
		// Fill the border values with anti-symmetric bounding conditions
		for (int i = 0; i < intervals+3; i++)
			for (int j = 0; j < intervals+3; j++)
			{
				int iFrom = i;
				int jFrom = j;
				int iPivot = -1;
				int jPivot = -1;

				if(iFrom < 1)
				{
					iFrom = 2 * 1 - i;
					iPivot = 1;
					jPivot = j;
				}
				else if(iFrom > (intervals+1))
				{
					iFrom = 2 * (intervals+1) - i;
					iPivot = intervals+1;
					jPivot = j;
				}
				if(jFrom < 1)
				{
					jFrom = 2 * 1 - j;
					jPivot = 1;
					iPivot = (iPivot != -1) ? iPivot : i;
				}
				else if(jFrom > (intervals+1))
				{
					jFrom = 2 * (intervals+1) - j;
					jPivot = intervals+1;
					iPivot = (iPivot != -1) ? iPivot : i;
				}

				if(iPivot != -1 && jPivot != -1)
				{
					cx[i][j] = 2 * cx[iPivot][jPivot] - cx[iFrom][jFrom];
					cy[i][j] = 2 * cy[iPivot][jPivot] - cy[iFrom][jFrom];
				}
			}
		

	} // end convertRawTransformationToBSpline 	
	
	
	
	
	/*------------------------------------------------------------------
	/**
	 * Invert the raw transformation (approximation)
	 *
	 * @param targetImp target image representation
	 * @param transformation_x raw transformation in x- axis (input)
	 * @param transformation_y raw transformation in y- axis (input)
	 * @param inv_x raw transformation in x- axis (output)
	 * @param inv_y raw transformation in y- axis (output)
	 */
	public static void invertRawTransformation(
			ImagePlus targetImp,
			double [][] transformation_x,
			double [][] transformation_y,
			double [][] inv_x,
			double [][] inv_y)
	{

		if(inv_x == null || inv_y == null || transformation_x == null || transformation_y == null)
		{
			IJ.error("Error in transformations parameters!");
			return;
		}

		// Extract height and width information
		final int targetCurrentHeight = targetImp.getProcessor().getHeight();
		final int targetCurrentWidth  = targetImp.getProcessor().getWidth ();
				
				
		// Approach inverse transform
		for (int i = 0; i < targetCurrentHeight; i++)
			for (int j = 0; j < targetCurrentWidth; j++)
			{
				final int originX =(int) Math.round(transformation_x[i][j]);
				final int originY =(int) Math.round(transformation_y[i][j]);
				
				if(originX >= 0 && originX < targetCurrentWidth && originY >= 0 && originY < targetCurrentHeight)
				{
					inv_x[originY][originX] = j; 
					inv_y[originY][originX] = i;
				}
		
			}
		
		// Replace empty transformation positions
		for (int i = 0; i < targetCurrentHeight; i++)
			for (int j = 0; j < targetCurrentWidth; j++)
			{
				if(inv_x[i][j] == 0 && inv_y[i][j] == 0)
				{
					double val_x = 0;
					double val_y = 0;
					int n = 0;
					
					if(i > 0)
					{
						if(inv_x[i-1][j] != 0 && inv_y[i-1][j] != 0)
						{
							val_x += inv_x[i-1][j];
							val_y += inv_y[i-1][j];
							n++;
						}
						if(j > 0 && inv_x[i-1][j-1] != 0 && inv_y[i-1][j-1] != 0)
						{
							val_x += inv_x[i-1][j-1];
							val_y += inv_y[i-1][j-1];
							n++;
						}
						if(j < targetCurrentWidth-1 && inv_x[i-1][j+1] != 0 && inv_y[i-1][j+1] != 0)
						{
							val_x += inv_x[i-1][j+1];
							val_y += inv_y[i-1][j+1];
							n++;
						}
					}
					
					if(i < targetCurrentHeight-1)
					{
						if(inv_x[i+1][j] != 0 && inv_y[i+1][j] != 0)
						{
							val_x += inv_x[i+1][j];
							val_y += inv_y[i+1][j];
							n++;
						}
						if(j > 0 && inv_x[i+1][j-1] != 0 && inv_y[i+1][j-1] != 0)
						{
							val_x += inv_x[i+1][j-1];
							val_y += inv_y[i+1][j-1];
							n++;
						}
						if(j < targetCurrentWidth-1 && inv_x[i+1][j+1] != 0 && inv_y[i+1][j+1] != 0)
						{
							val_x += inv_x[i+1][j+1];
							val_y += inv_y[i+1][j+1];
							n++;
						}
					}
					
					if(j > 0 && inv_x[i][j-1] != 0 && inv_y[i][j-1] != 0)
					{
						val_x += inv_x[i][j-1];
						val_y += inv_y[i][j-1];
						n++;
					}
					
					if(j < targetCurrentWidth-1 && inv_x[i][j+1] != 0 && inv_y[i][j+1] != 0)
					{
						val_x += inv_x[i][j+1];
						val_y += inv_y[i][j+1];
						n++;
					}
					
					// Add mean value
					if(n != 0)
					{
						inv_x[i][j] += val_x / n;
						inv_y[i][j] += val_y / n;
					}
				}
			}
	} // end invertRawTransformation 	
	/**
	 * Approximate the inverse of a raw transform and save it to file.
	 * @param inputTransfPath complete path to input raw transform file
	 * @param outputTransfPath complete path to output transform file
	 * @param targetImp target image
	 */
	public static void invertRawTransformation(
			String inputTransfPath,
			String outputTransfPath,
			ImagePlus targetImp )
	{
		double[][] transformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		MiscTools.loadRawTransformation( inputTransfPath, transformation_x,
				transformation_y );

		double[][] inv_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] inv_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		MiscTools.invertRawTransformation( targetImp, transformation_x,
				transformation_y, inv_x, inv_y );

		MiscTools.saveRawTransformation( outputTransfPath, targetImp.getWidth(),
				targetImp.getHeight(), inv_x, inv_y );
	}

	//------------------------------------------------------------------
	/**
	 * Warping index for comparing elastic deformations with any kind
	 * of deformation (both transformations having same direction).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx_direct direct transformation x- B-spline coefficients
	 * @param cy_direct direct transformation y- B-spline coefficients
	 * @param transformation_x raw direct transformation in x- axis
	 * @param transformation_y raw direct transformation in y- axis
	 */
	public static double rawWarpingIndex(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			int intervals,
			double [][] cx_direct,
			double [][] cy_direct,
			double [][] transformation_x,
			double [][] transformation_y)
	{

		if(cx_direct == null || cy_direct == null || transformation_x == null || transformation_y == null)
		{
			IJ.error("Error in the raw warping index parameters!");
			return -1;
		}

		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();
		int sourceCurrentHeight = sourceImp.getProcessor().getHeight();
		int sourceCurrentWidth  = sourceImp.getProcessor().getWidth ();

		double [][] transformation_x_direct = new double [targetCurrentHeight][targetCurrentWidth];
		double [][] transformation_y_direct = new double [targetCurrentHeight][targetCurrentWidth];

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;

		// We pass the coefficients to a one-dimension array
		// Direct coefficients.
		double c_direct[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c_direct[n     ] = cx_direct[i][j];
				c_direct[n + Nk] = cy_direct[i][j];
			}

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx_direct = new BSplineModel(c_direct, cYdim, cXdim, 0);
		BSplineModel swy_direct = new BSplineModel(c_direct, cYdim, cXdim, Nk);


		swx_direct.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy_direct.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);


		// Compute the direct transformation mapping
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;

			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx_direct.prepareForInterpolation(tu, tv, false);
				transformation_x_direct[v][u] = swx_direct.interpolateI();

				swy_direct.prepareForInterpolation(tu, tv, false);
				transformation_y_direct[v][u] = swy_direct.interpolateI();
			}
		}


		// Compute the geometric error between both transformations
		double warpingIndex = 0;
		int n = 0;
		for (int v=0; v<targetCurrentHeight; v++)
			for (int u=0; u<targetCurrentWidth; u++)
			{
				// Calculate the mapping through the elastic deformation
				final double x_elastic = transformation_x_direct[v][u];
				final double y_elastic = transformation_y_direct[v][u];

				if (x_elastic>=0 && x_elastic<sourceCurrentWidth && y_elastic>=0 && y_elastic<sourceCurrentHeight)
				{
					double x_random = transformation_x[v][u];
					double y_random = transformation_y[v][u];

					double aux1 = x_elastic - x_random;
					double aux2 = y_elastic - y_random;

					warpingIndex += aux1 * aux1 + aux2 * aux2;

					n++; // Another point has been successfully evaluated
				}

			}

		if(n != 0)
		{
			warpingIndex /= (double) n;
			warpingIndex = Math.sqrt(warpingIndex);
		}
		else
			warpingIndex = -1;
		return warpingIndex;
	}
	//------------------------------------------------------------------
	/**
	 * Warping index for comparing two raw deformations (both 
	 * transformations having same direction).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param transformation_x_1 raw first transformation in x- axis
	 * @param transformation_y_1 raw first transformation in y- axis
	 * @param transformation_x_2 raw second transformation in x- axis
	 * @param transformation_y_2 raw second transformation in y- axis
	 */
	public static double rawWarpingIndex(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			double [][] transformation_x_1,
			double [][] transformation_y_1,
			double [][] transformation_x_2,
			double [][] transformation_y_2)
	{

		if(transformation_x_1 == null || transformation_y_1 == null || transformation_x_2 == null || transformation_y_2 == null)
		{
			IJ.error("Error in the raw warping index parameters!");
			return -1;
		}

		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();
		int sourceCurrentHeight = sourceImp.getProcessor().getHeight();
		int sourceCurrentWidth  = sourceImp.getProcessor().getWidth ();

		// Compute the geometrical error between both transformations
		double warpingIndex = 0;
		int n = 0;
		for (int v=0; v<targetCurrentHeight; v++)
			for (int u=0; u<targetCurrentWidth; u++)
			{
				// Calculate the mapping through the elastic deformation
				final double x1 = transformation_x_1[v][u];
				final double y1 = transformation_y_1[v][u];

				if (x1>=0 && x1<sourceCurrentWidth && y1>=0 && y1<sourceCurrentHeight)
				{
					double x2 = transformation_x_2[v][u];
					double y2 = transformation_y_2[v][u];

					double aux1 = x1 - x2;
					double aux2 = y1 - y2;

					warpingIndex += aux1 * aux1 + aux2 * aux2;

					n++; // Another point has been successfully evaluated
				}

			}

		if(n != 0)
		{
			warpingIndex /= (double) n;
			warpingIndex = Math.sqrt(warpingIndex);
		}
		else
			warpingIndex = -1;
		return warpingIndex;
	}
	//------------------------------------------------------------------

	/**
	 * Draw an arrow between two points.
	 * The arrow head is in (x2,y2)
	 *
	 * @param canvas canvas where we are painting
	 * @param x1 x- coordinate for the arrow origin
	 * @param y1 y- coordinate for the arrow origin
	 * @param x2 x- coordinate for the arrow head
	 * @param y2 y- coordinate for the arrow head
	 * @param color arrow color
	 * @param arrow_size arrow size
	 */
	static public void drawArrow(double [][]canvas, int x1, int y1,
			int x2, int y2, double color, int arrow_size)
	{
		drawLine(canvas,x1,y1,x2,y2,color);
		int arrow_size2 = 2 * arrow_size;

		// Do not draw the arrow_head if the arrow is very small
		if ((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)<arrow_size*arrow_size) return;

		// Vertical arrow
		if (x2 == x1) {
			if (y2 > y1) {
				drawLine(canvas,x2,y2,x2-arrow_size,y2-arrow_size2,color);
				drawLine(canvas,x2,y2,x2+arrow_size,y2-arrow_size2,color);
			} else {
				drawLine(canvas,x2,y2,x2-arrow_size,y2+arrow_size2,color);
				drawLine(canvas,x2,y2,x2+arrow_size,y2+arrow_size2,color);
			}
		}

		// Horizontal arrow
		else if (y2 == y1) {
			if (x2 > x1) {
				drawLine(canvas,x2,y2,x2-arrow_size2,y2-arrow_size,color);
				drawLine(canvas,x2,y2,x2-arrow_size2,y2+arrow_size,color);
			} else {
				drawLine(canvas,x2,y2,x2+arrow_size2,y2-arrow_size,color);
				drawLine(canvas,x2,y2,x2+arrow_size2,y2+arrow_size,color);
			}
		}

		// Now we need to rotate the arrow head about the origin
		else {
			// Calculate the angle of rotation and adjust for the quadrant
			double t1 = Math.abs(new Integer(y2 - y1).doubleValue());
			double t2 = Math.abs(new Integer(x2 - x1).doubleValue());
			double theta = Math.atan(t1 / t2);
			if (x2 < x1) {
				if (y2 < y1) theta = Math.PI + theta;
				else         theta = - (Math.PI + theta);
			} else if (x2 > x1 && y2 < y1)
				theta =  2*Math.PI - theta;
			double cosTheta = Math.cos(theta);
			double sinTheta = Math.sin(theta);

			// Create the other points and translate the arrow to the origin
			Point p2 = new Point(-arrow_size2,-arrow_size);
			Point p3 = new Point(-arrow_size2,+arrow_size);

			// Rotate the points (without using matrices!)
			int x = new Long(Math.round((cosTheta * p2.x) - (sinTheta * p2.y))).intValue();
			p2.y = new Long(Math.round((sinTheta * p2.x) + (cosTheta * p2.y))).intValue();
			p2.x = x;
			x = new Long(Math.round((cosTheta * p3.x) - (sinTheta * p3.y))).intValue();
			p3.y = new Long(Math.round((sinTheta * p3.x) + (cosTheta * p3.y))).intValue();
			p3.x = x;

			// Translate back to desired location and add to polygon
			p2.translate(x2,y2);
			p3.translate(x2,y2);
			drawLine(canvas,x2,y2,p2.x,p2.y,color);
			drawLine(canvas,x2,y2,p3.x,p3.y,color);
		}
	}

	//------------------------------------------------------------------
	/**
	 * Draw a line between two points.
	 * Bresenham's algorithm.
	 *
	 * @param canvas canvas where we are painting
	 * @param x1 x- coordinate for first point
	 * @param y1 y- coordinate for first point
	 * @param x2 x- coordinate for second point
	 * @param y2 y- coordinate for second point
	 * @param color line color
	 */
	static public void drawLine(double [][]canvas, int x1, int y1,
			int x2, int y2, double color)
	{
		int temp;
		int dy_neg = 1;
		int dx_neg = 1;
		int switch_x_y = 0;
		int neg_slope = 0;
		int tempx, tempy;
		int dx = x2 - x1;
		if(dx == 0)
			if(y1 > y2) {
				for(int n = y2; n <= y1; n++) Point(canvas,n,x1,color);
				return;
			} else {
				for(int n = y1; n <= y2; n++) Point(canvas,n,x1,color);
				return;
			}

		int dy = y2 - y1;
		if(dy == 0)
			if(x1 > x2) {
				for(int n = x2; n <= x1; n++) Point(canvas,y1,n,color);
				return;
			} else {
				for(int n = x1; n <= x2; n++) Point(canvas,y1,n,color);
				return;
			}

		float m = (float) dy/dx;

		if(m > 1 || m < -1) {
			temp = x1;
			x1 = y1;
			y1 = temp;
			temp = x2;
			x2 = y2;
			y2 = temp;
			dx = x2 - x1;
			dy = y2 - y1;
			m = (float) dy/dx;
			switch_x_y = 1;
		}

		if(x1 > x2) {
			temp = x1;
			x1 = x2;
			x2 = temp;
			temp = y1;
			y1 = y2;
			y2 = temp;
			dx = x2 - x1;
			dy = y2 - y1;
			m = (float) dy/dx;
		}

		if(m < 0) {
			if(dy < 0) {
				dy_neg = -1;
				dx_neg = 1;
			} else {
				dy_neg = 1;
				dx_neg = -1;
			}
			neg_slope = 1;
		}

		int d = 2 * (dy * dy_neg) - (dx * dx_neg);
		int incrH = 2 * dy * dy_neg;
		int incrHV = 2 * ( (dy * dy_neg)  - (dx * dx_neg) );
		int x = x1;
		int y = y1;
		tempx = x;
		tempy = y;

		if(switch_x_y == 1) {
			temp = x;
			x = y;
			y = temp;
		}
		Point(canvas,y,x,color);
		x = tempx;
		y = tempy;

		while(x < x2) {
			if(d <= 0) {
				x++;
				d += incrH;
			} else {
				d += incrHV;
				x++;
				if(neg_slope == 0) y++;
				else               y--;
			}
			tempx = x;
			tempy = y;

			if (switch_x_y == 1) {
				temp = x;
				x = y;
				y = temp;
			}
			Point(canvas,y,x,color);
			x = tempx;
			y = tempy;
		}
	}

	//------------------------------------------------------------------
	/**
	 * Put the image from an ImageProcessor into a double array.
	 *
	 * @param ip input, origin of the image
	 * @param image output, the image in a double array
	 */
	static public void extractImage(final ImageProcessor ip, double image[])
	{
		int k=0;
		int height=ip.getHeight();
		int width =ip.getWidth ();
		if (ip instanceof ByteProcessor) 
		{
			final byte[] pixels = (byte[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					image[k] = (double)(pixels[k] & 0xFF);
		} 
		else if (ip instanceof ShortProcessor) 
		{
			final short[] pixels = (short[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					if (pixels[k] < (short)0) image[k] = (double)pixels[k] + 65536.0F;
					else                      image[k] = (double)pixels[k];
		} 
		else if (ip instanceof FloatProcessor) 
		{
			final float[] pixels = (float[])ip.getPixels();
			for (int p = 0; p<height*width; p++)
				image[p]=pixels[p];
		}
		else if (ip instanceof ColorProcessor)
		{
			ImageProcessor fp = ip.convertToFloat();
			final float[] pixels = (float[])fp.getPixels();
			for (int p = 0; p<height*width; p++)
				image[p] = pixels[p];    	  
		}
	}

	//------------------------------------------------------------------
	/**
	 * Put the image from an ImageProcessor into a double[][].
	 *
	 * @param ip input, origin of the image
	 * @param image output, the image in a double[][]
	 */
	static public void extractImage(final ImageProcessor ip, double image[][])
	{
		int k=0;
		int height=ip.getHeight();
		int width =ip.getWidth ();
		if (ip instanceof ByteProcessor) {
			final byte[] pixels = (byte[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					image[y][x] = (pixels[k] & 0xFF);
		} else if (ip instanceof ShortProcessor) {
			final short[] pixels = (short[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					if (pixels[k] < (short)0) image[y][x] = (double)pixels[k] + 65536.0F;
					else                      image[y][x] = (double)pixels[k];
		} else if (ip instanceof FloatProcessor) {
			final float[] pixels = (float[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					image[y][x]=pixels[k];
		}
	}

	//------------------------------------------------------------------
	/**
	 * Put the image from an ImageProcessor into a int[][]. Only valid for grayscale images
	 *
	 * @param ip input, the ImageProcessor containing the image
	 * @param image output, the image in a int[][]
	 */
	static public void extractImage(final ImageProcessor ip, int image[][])
	{
		int k=0;
		int height=ip.getHeight();
		int width =ip.getWidth ();
		if (ip instanceof ByteProcessor) {
			final byte[] pixels = (byte[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					image[y][x] = (pixels[k] & 0xFF);
		} else if (ip instanceof ShortProcessor) {
			final short[] pixels = (short[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					if (pixels[k] < (short)0) image[y][x] = (int)(pixels[k] + 65536.0F);
					else                      image[y][x] = pixels[k];
		} else if (ip instanceof FloatProcessor) {
			final float[] pixels = (float[])ip.getPixels();
			for (int y = 0; (y < height); y++)
				for (int x = 0; (x < width); x++, k++)
					image[y][x] = Math.round(pixels[k]);
		}
	}

	//------------------------------------------------------------------
	/**
	 * Put the image from an ImageProcessor into a 1D int array. Only valid for grayscale images
	 *
	 * @param ip input, the ImageProcessor containing the image
	 * @param image output, the image in an int array, which is the 2D image matrix concatenated by row
	 */
	static public void extractImage(final ImageProcessor ip, int image[])
	{
		int k = 0;
		int height = ip.getHeight();
		int width = ip.getWidth();

		if (ip instanceof ByteProcessor) {
			final byte[] pixels = (byte[]) ip.getPixels();
			for (int y = 0; (y < height); y++) {
				for (int x = 0; (x < width); x++, k++) {
					int arrayIdx = (y * width) + x;
					image[arrayIdx] = (pixels[k] & 0xFF);
				}
			}
		} else if (ip instanceof ShortProcessor) {
			final short[] pixels = (short[])ip.getPixels();
			for (int y = 0; (y < height); y++) {
				for (int x = 0; (x < width); x++, k++) {
					int arrayIdx = (y * width) + x;
					if (pixels[k] < (short) 0)
						image[arrayIdx] = (int) (pixels[k] + 65536.0F);
					else
						image[arrayIdx] = pixels[k];
				}
			}
		} else if (ip instanceof FloatProcessor) {
			final float[] pixels = (float[])ip.getPixels();
			for (int y = 0; (y < height); y++) {
				for (int x = 0; (x < width); x++, k++) {
					int arrayIdx = (y * width) + x;
					image[arrayIdx] = Math.round(pixels[k]);
				}
			}
		}
	}

	//------------------------------------------------------------------
	/**
	 * Create a ByteProcessor ImagePlus from a double[][].
	 *
	 * @param image input, the image in an int[][], the values should be scaled 0-255
	 * @param imageTitle input, the title to assign to the resulting ImagePlus
	 */
	static public ImagePlus createImagePlusByte(int image[][], String imageTitle)
	{
		int height=image.length;
		int width =image[0].length;
		ByteProcessor imageProcByte = new ByteProcessor(width, height);
		for (int y = 0; (y < height); y++) {
			for (int x = 0; (x < width); x++) {
				imageProcByte.set(x,y,image[y][x]);
			}
		}
		return(new ImagePlus(imageTitle,imageProcByte));
	}

	//------------------------------------------------------------------
	/**
	 * Create a FloatProcessor ImagePlus from a float[][].
	 *
	 * @param image input, the image in a float[][]
	 * @param imageTitle input, the title to assign to the resulting ImagePlus
	 */
	static public ImagePlus createImagePlusFloat(float image[][], String imageTitle)
	{
		FloatProcessor imageProcFloat = new FloatProcessor(image);
		return(new ImagePlus(imageTitle,imageProcFloat));
	}

	//------------------------------------------------------------------
	/**
	 * Load landmarks from file.
	 *
	 * @param filename landmarks file name
	 * @param sourceStack stack of source related points (output)
	 * @param targetStack stack of target related points (output)
	 */
	static public void loadPoints(String filename,
			Stack <Point> sourceStack, Stack <Point> targetStack)
	{
		Point sourcePoint;
		Point targetPoint;
		try {
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;
			String index;
			String xSource;
			String ySource;
			String xTarget;
			String yTarget;
			int separatorIndex;
			int k = 1;
			if (!(line = br.readLine()).equals("Index\txSource\tySource\txTarget\tyTarget")) {
				fr.close();
				IJ.log("Line " + k + ": 'Index\txSource\tySource\txTarget\tyTarget'");
				return;
			}
			++k;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				separatorIndex = line.indexOf('\t');
				if (separatorIndex == -1) {
					br.close();
					fr.close();
					IJ.log("Line " + k
							+ ": #Index# <tab> #xSource# <tab> #ySource# <tab> #xTarget# <tab> #yTarget#");
					return;
				}
				index = line.substring(0, separatorIndex);
				index = index.trim();
				line = line.substring(separatorIndex);
				line = line.trim();
				separatorIndex = line.indexOf('\t');
				if (separatorIndex == -1) {
					br.close();
					fr.close();
					IJ.log("Line " + k
							+ ": #Index# <tab> #xSource# <tab> #ySource# <tab> #xTarget# <tab> #yTarget#");
					return;
				}
				xSource = line.substring(0, separatorIndex);
				xSource = xSource.trim();
				line = line.substring(separatorIndex);
				line = line.trim();
				separatorIndex = line.indexOf('\t');
				if (separatorIndex == -1) {
					br.close();
					fr.close();
					IJ.log("Line " + k
							+ ": #Index# <tab> #xSource# <tab> #ySource# <tab> #xTarget# <tab> #yTarget#");
					return;
				}
				ySource = line.substring(0, separatorIndex);
				ySource = ySource.trim();
				line = line.substring(separatorIndex);
				line = line.trim();
				separatorIndex = line.indexOf('\t');
				if (separatorIndex == -1) {
					br.close();
					fr.close();
					IJ.log("Line " + k
							+ ": #Index# <tab> #xSource# <tab> #ySource# <tab> #xTarget# <tab> #yTarget#");
					return;
				}
				xTarget = line.substring(0, separatorIndex);
				xTarget = xTarget.trim();
				yTarget = line.substring(separatorIndex);
				yTarget = yTarget.trim();
				sourcePoint = new Point(Integer.valueOf(xSource).intValue(),
						Integer.valueOf(ySource).intValue());
				sourceStack.push(sourcePoint);
				targetPoint = new Point(Integer.valueOf(xTarget).intValue(),
						Integer.valueOf(yTarget).intValue());
				targetStack.push(targetPoint);
			}
			fr.close();
		} catch (FileNotFoundException e) {
			IJ.error("File not found exception" + e);
			return;
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
			return;
		} catch (NumberFormatException e) {
			IJ.error("Number format exception" + e);
			return;
		}
	}
	
	//------------------------------------------------------------------
	/**
	 * Load point rois in the source and target images as landmarks.
	 * 
	 * @param sourceImp source image plus
	 * @param targetImp target image plus 
	 * @param sourceStack stack of source related points (output)
	 * @param targetStack stack of target related points (output)
	 */
	public static void loadPointRoiAsLandmarks(ImagePlus sourceImp,
												 ImagePlus targetImp, 
												 Stack <Point> sourceStack, 
												 Stack <Point> targetStack)
	{

		Roi roiSource = sourceImp.getRoi();
		Roi roiTarget = targetImp.getRoi();				

		if(roiSource instanceof PointRoi && roiTarget instanceof PointRoi)
		{
			PointRoi prSource = (PointRoi) roiSource;
			int[] xSource = prSource.getXCoordinates();

			PointRoi prTarget = (PointRoi) roiTarget;
			int[] xTarget = prTarget.getXCoordinates();

			int numOfPoints = xSource.length;

			// If the number of points in both images is not the same,
			// we do nothing.
			if(numOfPoints != xTarget.length)
				return;

			// Otherwise we load the points in order as landmarks.
			int[] ySource = prSource.getYCoordinates();
			int[] yTarget = prTarget.getYCoordinates();

			// The coordinates from the point rois are relative to the
			// bounding box origin.
			Rectangle recSource = prSource.getBounds();
			int originXSource = recSource.x;
			int originYSource = recSource.y;

			Rectangle recTarget = prTarget.getBounds();
			int originXTarget = recTarget.x;
			int originYTarget = recTarget.y;

			for(int i = 0; i < numOfPoints; i++)
			{
				sourceStack.push(new Point(xSource[i] + originXSource, ySource[i] + originYSource));
				targetStack.push(new Point(xTarget[i] + originXTarget, yTarget[i] + originYTarget));
			}			
		}

	}
	/* end loadPointRoiAsLandmarks */
	
	//------------------------------------------------------------------
	/**
	 * Load a transformation from a file.
	 *
	 * @param filename transformation file name
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 */
	public static void loadTransformation(String filename,
			final double [][]cx, final double [][]cy)
	{
		try {
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;

			// Read number of intervals
			line = br.readLine();
			int lineN = 1;
			StringTokenizer st = new StringTokenizer(line,"=");
			if (st.countTokens()!=2)
			{
				br.close();
				fr.close();
				IJ.log("Line "+lineN+"+: Cannot read number of intervals");
				return;
			}
			st.nextToken();
			int intervals=Integer.valueOf(st.nextToken()).intValue();

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the cx coefficients
			for (int i= 0; i<intervals+3; i++)
			{
				line = br.readLine(); lineN++;
				st=new StringTokenizer(line);
				if (st.countTokens()!=intervals+3)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coefficients");
					return;
				}
				for (int j=0; j<intervals+3; j++)
					cx[i][j]=Double.valueOf(st.nextToken()).doubleValue();
			}

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the cy coefficients
			for (int i= 0; i<intervals+3; i++)
			{
				line = br.readLine(); lineN++;
				st = new StringTokenizer(line);
				if (st.countTokens()!=intervals+3)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coefficients");
					return;
				}
				for (int j=0; j<intervals+3; j++)
					cy[i][j]=Double.valueOf(st.nextToken()).doubleValue();
			}
			fr.close();
		} catch (FileNotFoundException e) {
			IJ.error("File not found exception" + e);
			return;
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
			return;
		} catch (NumberFormatException e) {
			IJ.error("Number format exception" + e);
			return;
		}
	}

	//------------------------------------------------------------------
	/**
	 * Load a raw transformation from a file.
	 *
	 * @param filename transformation file name
	 * @param transformation_x output x- transformation coordinates
	 * @param transformation_y output y- transformation coordinates
	 */
	static public void loadRawTransformation(String filename,
			double [][]transformation_x, double [][]transformation_y)
	{
		try
		{
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;

			// Read width
			line = br.readLine();
			int lineN = 1;
			StringTokenizer st = new StringTokenizer(line,"=");
			if (st.countTokens() != 2)
			{
				fr.close();
				IJ.log("Line "+lineN+"+: Cannot read transformation width");
				return;
			}
			st.nextToken();
			int width = Integer.valueOf(st.nextToken()).intValue();

			// Read height
			line = br.readLine();
			lineN ++;
			st = new StringTokenizer(line,"=");
			if (st.countTokens() != 2)
			{
				fr.close();
				IJ.log("Line " + lineN + "+: Cannot read transformation height");
				return;
			}
			st.nextToken();
			int height = Integer.valueOf(st.nextToken()).intValue();

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the X transformation coordinates
			for (int i= 0; i < height; i++)
			{
				line = br.readLine(); lineN++;
				st = new StringTokenizer(line);
				if (st.countTokens() != width)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coordinates");
					return;
				}
				for (int j = 0; j < width; j++)
					transformation_x[i][j]  = Double.valueOf(st.nextToken()).doubleValue();
			}

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the Y transformation coordinates
			for (int i= 0; i < height; i++)
			{
				line = br.readLine(); lineN++;
				st = new StringTokenizer(line);
				if (st.countTokens() != width)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coordinates");
					return;
				}
				for (int j = 0; j < width; j++)
					transformation_y[i][j]  = Double.valueOf(st.nextToken()).doubleValue();
			}
			fr.close();
		}
		catch (FileNotFoundException e)
		{
			IJ.error("File not found exception" + e);
			return;
		}
		catch (IOException e)
		{
			IJ.error("IOException exception" + e);
			return;
		}
		catch (NumberFormatException e)
		{
			IJ.error("Number format exception" + e);
			return;
		}
	} // end method loadRawTransformation
	
	//------------------------------------------------------------------
	/**
	 * Load a raw transformation from a file.
	 *
	 * @param filename transformation file name
	 * @param transformation_x output x- transformation coordinates
	 * @param transformation_y output y- transformation coordinates
	 */
	static public void loadRawTransformation(String filename,
			double []transformation_x, double []transformation_y)
	{
		try
		{
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;

			// Read width
			line = br.readLine();
			int lineN = 1;
			StringTokenizer st = new StringTokenizer(line,"=");
			if (st.countTokens() != 2)
			{
				fr.close();
				IJ.log("Line "+lineN+"+: Cannot read transformation width");
				return;
			}
			st.nextToken();
			int width = Integer.valueOf(st.nextToken()).intValue();

			// Read height
			line = br.readLine();
			lineN ++;
			st = new StringTokenizer(line,"=");
			if (st.countTokens() != 2)
			{
				fr.close();
				IJ.log("Line " + lineN + "+: Cannot read transformation height");
				return;
			}
			st.nextToken();
			int height = Integer.valueOf(st.nextToken()).intValue();

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the X transformation coordinates
			for (int i= 0; i < height; i++)
			{
				line = br.readLine(); lineN++;
				st = new StringTokenizer(line);
				if (st.countTokens() != width)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coordinates");
					return;
				}
				for (int j = 0; j < width; j++)
					transformation_x[j + i * width]  = Double.valueOf(st.nextToken()).doubleValue();
			}

			// Skip next 2 lines
			line = br.readLine();
			line = br.readLine();
			lineN+=2;

			// Read the Y transformation coordinates
			for (int i= 0; i < height; i++)
			{
				line = br.readLine(); lineN++;
				st = new StringTokenizer(line);
				if (st.countTokens() != width)
				{
					br.close();
					fr.close();
					IJ.log("Line "+lineN+": Cannot read enough coordinates");
					return;
				}
				for (int j = 0; j < width; j++)
					transformation_y[j + i * width] = Double.valueOf(st.nextToken()).doubleValue();
			}
			fr.close();
		}
		catch (FileNotFoundException e)
		{
			IJ.error("File not found exception" + e);
			return;
		}
		catch (IOException e)
		{
			IJ.error("IOException exception" + e);
			return;
		}
		catch (NumberFormatException e)
		{
			IJ.error("Number format exception" + e);
			return;
		}
	} // end method loadRawTransformation

	//------------------------------------------------------------------
	/**
	 * Load an affine matrix from a file.
	 *
	 * @param filename matrix file name
	 * @param affineMatrix output affine matrix
	 */
	static public void loadAffineMatrix(String filename,
			double [][]affineMatrix)
	{
		try
		{
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;

			// Read width
			line = br.readLine();
			StringTokenizer st = new StringTokenizer(line," ");
			if (st.countTokens() != 6)
			{
				fr.close();
				IJ.log("Cannot read affine transformation matrix");
				return;
			}

			affineMatrix[0][0] = Double.valueOf(st.nextToken()).doubleValue();
			affineMatrix[0][1] = Double.valueOf(st.nextToken()).doubleValue();
			affineMatrix[1][0] = Double.valueOf(st.nextToken()).doubleValue();
			affineMatrix[1][1] = Double.valueOf(st.nextToken()).doubleValue();
			affineMatrix[0][2] = Double.valueOf(st.nextToken()).doubleValue();
			affineMatrix[1][2] = Double.valueOf(st.nextToken()).doubleValue();

			fr.close();
		}
		catch (FileNotFoundException e)
		{
			IJ.error("File not found exception" + e);
			return;
		}
		catch (IOException e)
		{
			IJ.error("IOException exception" + e);
			return;
		}
		catch (NumberFormatException e)
		{
			IJ.error("Number format exception" + e);
			return;
		}
	}    /* end loadAffineMatrix */


	//------------------------------------------------------------------
	/**
	 * Compose two elastic deformations into a raw deformation.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx1 first transformation x- B-spline coefficients
	 * @param cy1 first transformation y- B-spline coefficients
	 * @param cx2 second transformation x- B-spline coefficients
	 * @param cy2 second transformation y- B-spline coefficients
	 * @param outputTransformation_x output transformation coordinates in x-axis
	 * @param outputTransformation_y output transformation coordinates in y-axis
	 */
	public static void composeElasticTransformations(
			ImagePlus targetImp,
			int intervals,
			double [][] cx1,
			double [][] cy1,
			double [][] cx2,
			double [][] cy2,
			double [][] outputTransformation_x,
			double [][] outputTransformation_y)
	{

		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;

		// We pass the coefficients to a one-dimension array
		// Direct coefficients.
		double c1[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c1[n     ] = cx1[i][j];
				c1[n + Nk] = cy1[i][j];
			}

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx1 = new BSplineModel(c1, cYdim, cXdim, 0);
		BSplineModel swy1 = new BSplineModel(c1, cYdim, cXdim, Nk);

		// Inverse coefficients.
		double c2[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c2[n     ] = cx2[i][j];
				c2[n + Nk] = cy2[i][j];
			}

		BSplineModel swx2 = new BSplineModel(c2, cYdim, cXdim, 0);
		BSplineModel swy2 = new BSplineModel(c2, cYdim, cXdim, Nk);


		swx1.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy1.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);

		swx2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);

		// Compute the transformation mapping
		// Notice here that we apply first the second transformation
		// since we are actually filling the target image with
		// pixels of the source image.
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;

			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx2.prepareForInterpolation(tu, tv, false);
				final double x2 = swx2.interpolateI();

				swy2.prepareForInterpolation(tu, tv, false);
				final double y2 = swy2.interpolateI();

				final double tv2 = (double)(y2 * intervals) / (double)(targetCurrentHeight - 1) + 1.0F;

				final double tu2 = (double)(x2 * intervals) / (double)(targetCurrentWidth - 1) + 1.0F;

				swx1.prepareForInterpolation(tu2, tv2, false);
				outputTransformation_x[v][u] = swx1.interpolateI();

				swy1.prepareForInterpolation(tu2, tv2, false);
				outputTransformation_y[v][u] = swy1.interpolateI();
			}
		}

	}  /* end method composeElasticTransformations */

	//------------------------------------------------------------------
	/**
	 * Compose a raw deformation and an elastic deformation into a raw deformation.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param transformation_x_1 first transformation coordinates in x-axis
	 * @param transformation_y_1 first transformation coordinates in y-axis
	 * @param cx2 second transformation x- B-spline coefficients
	 * @param cy2 second transformation y- B-spline coefficients
	 * @param outputTransformation_x output transformation coordinates in x-axis
	 * @param outputTransformation_y output transformation coordinates in y-axis
	 */
	public static void composeRawElasticTransformations(
			ImagePlus targetImp,
			int intervals,
			double [][] transformation_x_1,
			double [][] transformation_y_1,
			double [][] cx2,
			double [][] cy2,
			double [][] outputTransformation_x,
			double [][] outputTransformation_y)
	{
		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;


		// Inverse coefficients.
		double c2[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c2[n     ] = cx2[i][j];
				c2[n + Nk] = cy2[i][j];
			}

		BSplineModel swx2 = new BSplineModel(c2, cYdim, cXdim, 0);
		BSplineModel swy2 = new BSplineModel(c2, cYdim, cXdim, Nk);

		swx2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);

		// Compute the transformation mapping
		// Notice here that we apply first the second (elastic) transformation
		// since we are actually filling the target image with
		// pixels of the source image.
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;

			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				// Second transformation
				swx2.prepareForInterpolation(tu, tv, false);
				final double x2 = swx2.interpolateI();

				swy2.prepareForInterpolation(tu, tv, false);
				final double y2 = swy2.interpolateI();

				int xbase = (int) x2;
				int ybase = (int) y2;
				double xFraction = x2 - xbase;
				double yFraction = y2 - ybase;

				// First transformation.
				if(x2 >= 0 && x2 < targetCurrentWidth && y2 >= 0 && y2 < targetCurrentHeight)
				{
					// We apply bilinear interpolation
					final double lowerLeftX = transformation_x_1[ybase][xbase];
					final double lowerLeftY = transformation_y_1[ybase][xbase];

					final int xp1 = (xbase < (targetCurrentWidth -1)) ? xbase+1 : xbase;
					final int yp1 = (ybase < (targetCurrentHeight-1)) ? ybase+1 : ybase;

					final double lowerRightX = transformation_x_1[ybase][xp1];
					final double lowerRightY = transformation_y_1[ybase][xp1];

					final double upperRightX = transformation_x_1[yp1][xp1];
					final double upperRightY = transformation_y_1[yp1][xp1];

					final double upperLeftX = transformation_x_1[yp1][xbase];
					final double upperLeftY = transformation_y_1[yp1][xbase];

					final double upperAverageX = upperLeftX + xFraction * (upperRightX - upperLeftX);
					final double upperAverageY = upperLeftY + xFraction * (upperRightY - upperLeftY);
					final double lowerAverageX = lowerLeftX + xFraction * (lowerRightX - lowerLeftX);
					final double lowerAverageY = lowerLeftY + xFraction * (lowerRightY - lowerLeftY);

					outputTransformation_x[v][u] = lowerAverageX + yFraction * (upperAverageX - lowerAverageX);
					outputTransformation_y[v][u] = lowerAverageY + yFraction * (upperAverageY - lowerAverageY);
				}
				else
				{
					outputTransformation_x[v][u] = x2;
					outputTransformation_y[v][u] = y2;
				}

			}
		}

	}  /* end method composeRawElasticTransformations */

	//------------------------------------------------------------------
	/**
	 * Compose two elastic deformations into a raw deformation at pixel level.
	 *
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx1 first transformation x- B-spline coefficients
	 * @param cy1 first transformation y- B-spline coefficients
	 * @param cx2 second transformation x- B-spline coefficients
	 * @param cy2 second transformation y- B-spline coefficients
	 * @param outputTransformation_x output transformation coordinates in y-axis
	 * @param outputTransformation_y output transformation coordinates in y-axis
	 */
	public static void composeElasticTransformationsAtPixelLevel(
			ImagePlus targetImp,
			int intervals,
			double [][] cx1,
			double [][] cy1,
			double [][] cx2,
			double [][] cy2,
			double [][] outputTransformation_x,
			double [][] outputTransformation_y)
	{
		// Ask for memory for the transformation
		int targetCurrentHeight = targetImp.getProcessor().getHeight();
		int targetCurrentWidth  = targetImp.getProcessor().getWidth ();

		double [][] transformation_x_1 = new double [targetCurrentHeight][targetCurrentWidth];
		double [][] transformation_y_1 = new double [targetCurrentHeight][targetCurrentWidth];

		double [][] transformation_x_2 = new double [targetCurrentHeight][targetCurrentWidth];
		double [][] transformation_y_2 = new double [targetCurrentHeight][targetCurrentWidth];

		int cYdim = intervals+3;
		int cXdim = cYdim;
		int Nk = cYdim * cXdim;
		int twiceNk = 2 * Nk;

		// We pass the coefficients to a one-dimension array
		// Direct coefficients.
		double c1[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c1[n     ] = cx1[i][j];
				c1[n + Nk] = cy1[i][j];
			}

		// Compute the deformation
		// Set these coefficients to an interpolator
		BSplineModel swx1 = new BSplineModel(c1, cYdim, cXdim, 0);
		BSplineModel swy1 = new BSplineModel(c1, cYdim, cXdim, Nk);

		// Inverse coefficients.
		double c2[] = new double[twiceNk];
		for(int n = 0, i = 0; i < cYdim; i++)
			for(int j = 0; j < cYdim; j++, n++)
			{
				c2[n     ] = cx2[i][j];
				c2[n + Nk] = cy2[i][j];
			}

		BSplineModel swx2 = new BSplineModel(c2, cYdim, cXdim, 0);
		BSplineModel swy2 = new BSplineModel(c2, cYdim, cXdim, Nk);


		swx1.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy1.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);

		swx2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);
		swy2.precomputed_prepareForInterpolation(
				targetCurrentHeight, targetCurrentWidth, intervals);

		// Compute the first transformation mapping
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;

			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx1.prepareForInterpolation(tu, tv, false);
				transformation_x_1[v][u] = swx1.interpolateI();

				swy1.prepareForInterpolation(tu, tv, false);
				transformation_y_1[v][u] = swy1.interpolateI();
			}
		}

		// Compute the second transformation mapping
		for (int v=0; v<targetCurrentHeight; v++)
		{
			//prevent NaN result from divide by 0
			final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;
			for (int u = 0; u<targetCurrentWidth; u++)
			{
				//prevent NaN result from divide by 0
				final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;

				swx2.prepareForInterpolation(tu, tv, false);
				transformation_x_2[v][u] = swx2.interpolateI();

				swy2.prepareForInterpolation(tu, tv, false);
				transformation_y_2[v][u] = swy2.interpolateI();
			}
		}

		MiscTools.composeRawTransformations(targetCurrentWidth, targetCurrentHeight,
				transformation_x_1, transformation_y_1, transformation_x_2, transformation_y_2,
				outputTransformation_x, outputTransformation_y);
	}
	/**
	 * Compose raw transforms into another raw transform and save to file.
	 * @param transfPath1 complete path to first raw transform
	 * @param transfPath2 complete path to second raw transform
	 * @param outputTransfPath complete path to output file
	 * @param targetImp target image
	 */
	public static void composeRawTransforms(
			String transfPath1,
			String transfPath2,
			String outputTransfPath,
			ImagePlus targetImp )
	{
		// We load the first transformation raw file.
		double[][] transformation_x_1 =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y_1 =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		MiscTools.loadRawTransformation( transfPath1, transformation_x_1,
				transformation_y_1 );

		// We load the second transformation raw file.
		double[][] transformation_x_2 =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y_2 =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		MiscTools.loadRawTransformation( transfPath2, transformation_x_2,
				transformation_y_2 );

		double [][] outputTransformation_x =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];
		double [][] outputTransformation_y =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];

		// Now we compose them and get as result a raw transformation mapping.
		MiscTools.composeRawTransformations( targetImp.getWidth(),
				targetImp.getHeight(), transformation_x_1, transformation_y_1,
				transformation_x_2, transformation_y_2,	outputTransformation_x,
				outputTransformation_y );
		// save into file
		MiscTools.saveRawTransformation(outputTransfPath, targetImp.getWidth(),
				targetImp.getHeight(), outputTransformation_x,
				outputTransformation_y );
	}
	/**
	 * Compose two elastic transform into a raw transform and save it to file
	 * @param elasticTransfPath1 complete path to first elastic transform
	 * @param elasticTransfPath2 complete path to second elastic transform
	 * @param outputRawTransfPath complete path to output raw transform file
	 * @param targetImp target image
	 */
	public static void composeElasticTransforms(
			final String elasticTransfPath1,
			final String elasticTransfPath2,
			final String outputRawTransfPath,
			final ImagePlus targetImp )
	{
		int intervals =
				MiscTools.numberOfIntervalsOfTransformation( elasticTransfPath1 );

		double [][]cx1 = new double[intervals+3][intervals+3];
		double [][]cy1 = new double[intervals+3][intervals+3];

		MiscTools.loadTransformation(elasticTransfPath1, cx1, cy1);

		intervals =
				MiscTools.numberOfIntervalsOfTransformation( elasticTransfPath2 );

		double [][]cx2 = new double[intervals+3][intervals+3];
		double [][]cy2 = new double[intervals+3][intervals+3];

		MiscTools.loadTransformation(elasticTransfPath1, cx2, cy2);

		double [][] outputTransformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double [][] outputTransformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		// Now we compose them and get as result a raw transformation mapping.
		MiscTools.composeElasticTransformations( targetImp, intervals, cx1, cy1,
				cx2, cy2, outputTransformation_x, outputTransformation_y );

		MiscTools.saveRawTransformation( outputRawTransfPath,
				targetImp.getWidth(), targetImp.getHeight(),
				outputTransformation_x,	outputTransformation_y );
	}
	//------------------------------------------------------------------
	/**
	 * Compose two raw transformations (Bilinear interpolation)
	 *
	 * @param width image width
	 * @param height image height
	 * @param transformation_x_1 first transformation coordinates in x-axis
	 * @param transformation_y_1 first transformation coordinates in y-axis
	 * @param transformation_x_2 second transformation coordinates in x-axis
	 * @param transformation_y_2 second transformation coordinates in y-axis
	 * @param outputTransformation_x output transformation coordinates in y-axis
	 * @param outputTransformation_y output transformation coordinates in y-axis
	 */
	public static void composeRawTransformations(
			int          width,
			int          height,
			double [][]  transformation_x_1,
			double [][]  transformation_y_1,
			double [][]  transformation_x_2,
			double [][]  transformation_y_2,
			double [][]  outputTransformation_x,
			double [][]  outputTransformation_y)
	{
		// Notice here that we apply first the second transformation
		// since we are actually filling the target image with
		// pixels of the source image.
		for (int i= 0; i < height; i++)
			for (int j = 0; j < width; j++)
			{
				// Second transformation.
				double dX = transformation_x_2[i][j];
				double dY = transformation_y_2[i][j];
				int xbase = (int) dX;
				int ybase = (int) dY;
				double xFraction = dX - xbase;
				double yFraction = dY - ybase;

				// First transformation.
				if(dX >= 0 && dX < width && dY >= 0 && dY < height)
				{
					double lowerLeftX = transformation_x_1[ybase][xbase];
					double lowerLeftY = transformation_y_1[ybase][xbase];

					int xp1 = (xbase < (width -1)) ? xbase+1 : xbase;
					int yp1 = (ybase < (height-1)) ? ybase+1 : ybase;

					double lowerRightX = transformation_x_1[ybase][xp1];
					double lowerRightY = transformation_y_1[ybase][xp1];

					double upperRightX = transformation_x_1[yp1][xp1];
					double upperRightY = transformation_y_1[yp1][xp1];

					double upperLeftX = transformation_x_1[yp1][xbase];
					double upperLeftY = transformation_y_1[yp1][xbase];

					double upperAverageX = upperLeftX + xFraction * (upperRightX - upperLeftX);
					double upperAverageY = upperLeftY + xFraction * (upperRightY - upperLeftY);
					double lowerAverageX = lowerLeftX + xFraction * (lowerRightX - lowerLeftX);
					double lowerAverageY = lowerLeftY + xFraction * (lowerRightY - lowerLeftY);

					outputTransformation_x[i][j] = lowerAverageX + yFraction * (upperAverageX - lowerAverageX);
					outputTransformation_y[i][j] = lowerAverageY + yFraction * (upperAverageY - lowerAverageY);
				}
				else
				{
					outputTransformation_x[i][j] = dX;
					outputTransformation_y[i][j] = dY;
				}
			}
	}

	//------------------------------------------------------------------
	/**
	 * Save the elastic transformation.
	 *
	 * @param intervals number of intervals in the deformation
	 * @param cx x- deformation coefficients
	 * @param cy y- deformation coefficients
	 * @param filename transformation file name
	 */
	public static void saveElasticTransformation(
			int intervals,
			double [][]cx,
			double [][]cy,
			String filename)
	{

		// Save the file
		try {
			final FileWriter fw = new FileWriter(filename);
			String aux;
			fw.write("Intervals="+intervals+"\n\n");
			fw.write("X Coeffs -----------------------------------\n");
			for (int i= 0; i<intervals + 3; i++) 
			{
				for (int j = 0; j < intervals + 3; j++) 
				{
					aux=""+cx[i][j];
					while (aux.length()<21) 
						aux=" "+aux;
					fw.write(aux+" ");
				}
				fw.write("\n");
			}
			fw.write("\n");
			fw.write("Y Coeffs -----------------------------------\n");
			for (int i= 0; i<intervals + 3; i++) 
			{
				for (int j = 0; j < intervals + 3; j++) 
				{
					aux=""+cy[i][j];
					while (aux.length()<21) 
						aux=" "+aux;
					fw.write(aux+" ");
				}
				fw.write("\n");
			}
			fw.close();
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
		} catch (SecurityException e) {
			IJ.error("Security exception" + e);
		}
	}

	//------------------------------------------------------------------
	/**
	 * Save a raw transformation
	 *
	 * @param filename raw transformation file name
	 * @param width image width
	 * @param height image height
	 * @param transformation_x transformation coordinates in x-axis
	 * @param transformation_y transformation coordinates in y-axis
	 */
	public static void saveRawTransformation(
			String       filename,
			int          width,
			int          height,
			double [][]  transformation_x,
			double [][]  transformation_y)
	{
		if(filename == null || filename.equals(""))
		{
			String path = "";

			final OpenDialog od = new OpenDialog("Save Transformation", "");
			path = od.getDirectory();
			filename = od.getFileName();
			if ((path == null) || (filename == null)) return;
			filename = path+filename;

		}

		// Save the file
		try
		{
			final FileWriter fw = new FileWriter(filename);
			String aux;
			fw.write("Width=" + width +"\n");
			fw.write("Height=" + height +"\n\n");
			fw.write("X Trans -----------------------------------\n");
			for (int i= 0; i < height; i++)
			{
				for (int j = 0; j < width; j++)
				{
					aux="" + transformation_x[i][j];
					while (aux.length()<21) aux=" "+aux;
					fw.write(aux+" ");
				}
				fw.write("\n");
			}
			fw.write("\n");
			fw.write("Y Trans -----------------------------------\n");
			for (int i= 0; i < height; i++)
			{
				for (int j = 0; j < width; j++)
				{
					aux="" + transformation_y[i][j];
					while (aux.length()<21) aux=" "+aux;
					fw.write(aux+" ");
				}
				fw.write("\n");
			}
			fw.close();
		}
		catch (IOException e)
		{
			IJ.error("IOException exception" + e);
		}
		catch (SecurityException e)
		{
			IJ.error("Security exception" + e);
		}
	}

	//------------------------------------------------------------------
	/**
	 * Read the number of intervals of a transformation from a file.
	 *
	 * @param filename transformation file name
	 * @return number of intervals
	 */
	static public int numberOfIntervalsOfTransformation(String filename)
	{
		try {
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line;

			// Read number of intervals
			line = br.readLine();
			int lineN=1;
			StringTokenizer st=new StringTokenizer(line,"=");
			if (st.countTokens()!=2) {
				fr.close();
				IJ.log("Line "+lineN+"+: Cannot read number of intervals");
				return -1;
			}
			st.nextToken();
			int intervals=Integer.valueOf(st.nextToken()).intValue();

			fr.close();
			return intervals;
		} catch (FileNotFoundException e) {
			IJ.error("File not found exception" + e);
			return -1;
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
			return -1;
		} catch (NumberFormatException e) {
			IJ.error("Number format exception" + e);
			return -1;
		}
	}

	//------------------------------------------------------------------
	/**
	 * Plot a point in a canvas.
	 *
	 * @param canvas canvas where we are painting
	 * @param x x- coordinate for the point
	 * @param y y- coordinate for the point
	 * @param color point color
	 */
	static public void Point(double [][]canvas, int y, int x, double color)
	{
		if (y<0 || y>=canvas.length)    return;
		if (x<0 || x>=canvas[0].length) return;
		canvas[y][x]=color;
	}

	//------------------------------------------------------------------
	/**
	 * Print a matrix in the command line.
	 *
	 * @param title matrix title
	 * @param array matrix to be printed
	 */
	public static void printMatrix(
			final String    title,
			final double [][]array)
	{
		int Ydim=array.length;
		int Xdim=array[0].length;

		System.out.println(title);
		for (int i=0; i<Ydim; i++) {
			for (int j=0; j<Xdim; j++)
				System.out.print(array[i][j]+" ");
			System.out.println();
		}
	}

	//------------------------------------------------------------------
	/**
	 * Show an image in a new bUnwarpJ window.
	 *
	 * @param title image title
	 * @param array image in a double array
	 * @param Ydim image height
	 * @param Xdim image width
	 */
	public static void showImage(
			final String    title,
			final double []  array,
			final int       Ydim,
			final int       Xdim)
	{
		final FloatProcessor fp=new FloatProcessor(Xdim,Ydim);
		int ij=0;
		for (int i=0; i<Ydim; i++)
			for (int j=0; j<Xdim; j++, ij++)
				fp.setf(j,i, (float) array[ij]);
		fp.resetMinAndMax();
		final ImagePlus      ip=new ImagePlus(title, fp);
		ip.updateImage();
		ip.show();
	}

	//------------------------------------------------------------------
	/**
	 * Show an image in a new bUnwarpJ window.
	 *
	 * @param title image title
	 * @param array image in a double array
	 */
	public static void showImage(
			final String    title,
			final double [][]array)
	{
		int Ydim=array.length;
		int Xdim=array[0].length;

		final FloatProcessor fp=new FloatProcessor(Xdim,Ydim);
		for (int i=0; i<Ydim; i++)
			for (int j=0; j<Xdim; j++)
				fp.setf(j,i, (float) array[i][j]);
		fp.resetMinAndMax();
		final ImagePlus      ip=new ImagePlus(title, fp);
		ip.updateImage();
		ip.show();
	} // end showImage
	
	//------------------------------------------------------------------
	/**
	 * Adapt B-spline coefficients based on a scale factor.
	 * @param inputTransfPath complete path to input transform file
	 * @param scaleFactor isotropic scale factor
	 * @param outputTransfPath complete path to output transform file
	 */
	public static void adaptCoefficients(
			String inputTransfPath,
			double scaleFactor,
			String outputTransfPath )
	{
		int intervals =
				MiscTools.numberOfIntervalsOfTransformation( inputTransfPath );

		double [][]cx = new double[ intervals + 3 ][ intervals + 3 ];
		double [][]cy = new double[ intervals + 3 ][ intervals + 3 ];

		MiscTools.loadTransformation( inputTransfPath, cx, cy );

		// Adapt coefficients based on scale factor.
		for(int i = 0; i < (intervals+3); i++)
			for(int j = 0; j < (intervals+3); j++)
			{
				cx[i][j] *= scaleFactor;
				cy[i][j] *= scaleFactor;
			}

		MiscTools.saveElasticTransformation( intervals, cx, cy,
				outputTransfPath );
	}
	/**
	 * Adapt B-spline coefficients to a scale factor
	 * @param xScale
	 * @param yScale
	 * @param intervals
	 * @param cx
	 * @param cy
	 */
	public static void adaptCoefficients(
			double xScale,
			double yScale,		
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		for(int i = 0; i < (intervals+3); i++)
			for(int j = 0; j < (intervals+3); j++)
			{
				cx[i][j] *= xScale;
				cy[i][j] *= yScale;
			}
		
	} // end adaptCoefficients

	/**
	 * Apply the transformation to an ImagePlus
	 * @param aTransform
	 * @param aImage
	 * @return
	 */
	public static ImagePlus applyTransformationToImagePlus(Transformation aTransform, ImagePlus aImage) {
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

	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation to the source (gray-scale) image.
	 * The source image is modified. The target image is used to know
	 * the output size (Multi-thread version).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 */
	public static void applyTransformationToSourceMT(
			ImagePlus sourceImp,
			ImagePlus targetImp,			
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		BSplineModel source = new BSplineModel (sourceImp.getProcessor(), false, 1);
		source.setPyramidDepth(0);
		source.startPyramids();
		try {
			source.getThread().join();
		} catch (InterruptedException var8) {
			IJ.error("Unexpected interruption exception " + var8);
		}

		ImageProcessor result_imp = applyTransformationMT(sourceImp, targetImp, source, intervals, cx, cy);

		sourceImp.setProcessor(sourceImp.getTitle(), result_imp);
		sourceImp.updateImage();
		
	} // end applyTransformationToSourceMT	
	
	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation to the source (gray-scale) image.
	 * The source image is modified. The target image is used to know
	 * the output size (Multi-thread version).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param source source image model
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 */
	public static void applyTransformationToSourceMT(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			BSplineModel source,
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		ImageProcessor result_imp = applyTransformationMT(sourceImp, targetImp, source, intervals, cx, cy);

		sourceImp.setProcessor(sourceImp.getTitle(), result_imp);
		sourceImp.updateImage();
		
	} // end applyTransformationToSourceMT
	
	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation's coefficients to the source image.
	 * The result image is return. The target image is used to know
	 * the output size (Multi-thread version).
	 *
	 * @param sourceImp source image representation
	 * @param targetImp target image representation
	 * @param source source image model
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 * 
	 * @return result transformed image
	 */
	public static ImageProcessor applyTransformationMT(
			ImagePlus sourceImp,
			ImagePlus targetImp,
			BSplineModel source,
			int intervals,
			double [][]cx,
			double [][]cy)
	{
		final int targetHeight = targetImp.getProcessor().getHeight();
		final int targetWidth  = targetImp.getProcessor().getWidth();

		// Compute the warped image
		/* GRAY SCALE IMAGES */
		if(!(sourceImp.getProcessor() instanceof ColorProcessor))
		{
			return applyTransformationCoefficientsGreyscale(source, intervals, cx, cy, targetWidth, targetHeight);
		}
		else /* COLOR IMAGES */
		{        	
			return applyTransformationCoefficientsColor(sourceImp, intervals, cx, cy, targetWidth, targetHeight);
		}
	} // end applyTransformationMT

	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation's coefficients to the greyscale input image.
	 * The result image is returned as an ImageProcessor.  (Multi-thread version).
	 *
	 * @param source source image as a BSplineModel
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 * @param targetWidth width of the target image for which the transform was calculated
	 * @param targetHeight height of the target image for which the transform was calculated
	 *
	 * @return result transformed image
	 */
	public static ImageProcessor applyTransformationCoefficientsGreyscale(BSplineModel source,
																		  int intervals,
																		  double [][]cx,
																		  double [][]cy,
																		  int targetWidth,
																		  int targetHeight) {

		// Set these coefficients to an interpolator
		BSplineModel swx = new BSplineModel(cx);
		BSplineModel swy = new BSplineModel(cy);

		source.startPyramids();
		try{
			source.getThread().join();
		} catch (InterruptedException e) {
			IJ.error("Unexpected interruption exception " + e);
		}

		FloatProcessor fp = new FloatProcessor(targetWidth, targetHeight);

		// We will use threads to display parts of the output image

		// Check the number of processors in the computer
		final int nproc = Runtime.getRuntime().availableProcessors();

		//split rows as evenly as possible between available threads - smallest possible block height is 1
		int block_height = Math.max(targetHeight / nproc, 1);

		// Use one thread for each block
		final int nThreads = Math.min(nproc, targetHeight/block_height);

		Thread[] threads  = new Thread[nThreads];
		Rectangle[] rects = new Rectangle[nThreads];
		FloatProcessor[] fp_tile = new FloatProcessor[nThreads];

		for (int i=0; i<nThreads; i++)
		{
			// last block size is the rest of the window
			int y_start = i*block_height;

			if (nThreads-1 == i)
				block_height = targetHeight - i*block_height;

			rects[i] = new Rectangle(0, y_start, targetWidth, block_height);

			fp_tile[i] = new FloatProcessor(rects[i].width, rects[i].height);

			threads[i] = new Thread(new GrayscaleApplyTransformTile(swx, swy, source,
					targetWidth, targetHeight, intervals,
					rects[i], fp_tile[i]));
			threads[i].start();
		}

		for (int i=0; i<nThreads; i++)
		{
			try {
				threads[i].join();
				threads[i] = null;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		for (int i=0; i<nThreads; i++)
		{
			fp.insert(fp_tile[i], rects[i].x, rects[i].y);
			fp_tile[i] = null;
			rects[i] = null;
		}
		fp.resetMinAndMax();
		return fp;

	}

	/* --------------------------------------------------------------------*/
	/**
	 * Apply a given B-spline transformation's coefficients to the color input image.
	 * The result image is returned as an ImageProcessor.  (Multi-thread version).
	 *
	 * @param sourceImp source image representation
	 * @param intervals intervals in the deformation
	 * @param cx x- B-spline coefficients
	 * @param cy y- B-spline coefficients
	 * @param targetWidth width of the target image for which the transform was calculated
	 * @param targetHeight height of the target image for which the transform was calculated
	 *
	 * @return result transformed image
	 */
	public static ImageProcessor applyTransformationCoefficientsColor(ImagePlus sourceImp,
																	  int intervals,
																	  double [][]cx,
																	  double [][]cy,
																	  int targetWidth,
																	  int targetHeight) {

		// Set these coefficients to an interpolator
		BSplineModel swx = new BSplineModel(cx);
		BSplineModel swy = new BSplineModel(cy);

		// red
		BSplineModel sourceR = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(0, null), false, 1);
		sourceR.setPyramidDepth(0);
		sourceR.startPyramids();
		// green
		BSplineModel sourceG = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(1, null), false, 1);
		sourceG.setPyramidDepth(0);
		sourceG.startPyramids();
		//blue
		BSplineModel sourceB = new BSplineModel( ((ColorProcessor) (sourceImp.getProcessor())).toFloat(2, null), false, 1);
		sourceB.setPyramidDepth(0);
		sourceB.startPyramids();

		// Join threads
		try {
			sourceR.getThread().join();
			sourceG.getThread().join();
			sourceB.getThread().join();
		} catch (InterruptedException e) {
			IJ.error("Unexpected interruption exception " + e);
		}

		// Calculate warped RGB image
		ColorProcessor cp = new ColorProcessor(targetWidth, targetHeight);
		FloatProcessor fpR = new FloatProcessor(targetWidth, targetHeight);
		FloatProcessor fpG = new FloatProcessor(targetWidth, targetHeight);
		FloatProcessor fpB = new FloatProcessor(targetWidth, targetHeight);

		// We will use threads to display parts of the output image

		// Check the number of processors in the computer
		int nproc = Runtime.getRuntime().availableProcessors();

		//split rows as evenly as possible between available threads - smallest possible block height is 1
		int block_height = Math.max(targetHeight / nproc, 1);

		// Use one thread for each block
		final int nThreads = Math.min(nproc, targetHeight/block_height);

		Thread[] threads  = new Thread[nThreads];
		Rectangle[] rects = new Rectangle[nThreads];
		FloatProcessor[] fpR_tile 		= new FloatProcessor[nThreads];
		FloatProcessor[] fpG_tile 		= new FloatProcessor[nThreads];
		FloatProcessor[] fpB_tile 		= new FloatProcessor[nThreads];

		for (int i=0; i<nThreads; i++)
		{
			// last block size is the rest of the window
			int y_start = i*block_height;

			if (nThreads-1 == i)
				block_height = targetHeight - i*block_height;

			rects[i] = new Rectangle(0, y_start, targetWidth, block_height);

			//IJ.log("block = 0 " + (i*block_height) + " " + targetWidth + " " + block_height );

			fpR_tile[i] = new FloatProcessor(rects[i].width, rects[i].height);
			fpG_tile[i] = new FloatProcessor(rects[i].width, rects[i].height);
			fpB_tile[i] = new FloatProcessor(rects[i].width, rects[i].height);

			threads[i] = new Thread(new ColorApplyTransformTile(swx, swy, sourceR, sourceG, sourceB, targetWidth,
					targetHeight, intervals, rects[i], fpR_tile[i],
					fpG_tile[i], fpB_tile[i]));
			threads[i].start();
		}

		for (int i=0; i<nThreads; i++)
		{
			try {
				threads[i].join();
				threads[i] = null;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		for (int i=0; i<nThreads; i++)
		{
			fpR.insert(fpR_tile[i], rects[i].x, rects[i].y);
			fpG.insert(fpG_tile[i], rects[i].x, rects[i].y);
			fpB.insert(fpB_tile[i], rects[i].x, rects[i].y);

			fpR_tile[i] = null;
			fpG_tile[i] = null;
			fpB_tile[i] = null;

			rects[i] = null;
		}

		cp.setPixels(0, fpR);
		cp.setPixels(1, fpG);
		cp.setPixels(2, fpB);
		cp.resetMinAndMax();

		return cp;
	}

	/**
	 * Apply a given B-spline Transformation object to a gray-scale image.
	 *
	 * @param aTransform the Transformation to apply
	 * @param imageMtx the image as a 2D array of int values, should be scaled 0-255
	 *
	 * @return result transformed image as a matrix
	 */
	public static int[][] applyTransformationToGreyscaleImageMtx(Transformation aTransform, int[][] imageMtx) {

		BSplineModel source = new BSplineModel(imageMtx, false);
//		source.setPyramidDepth(0);
//		source.startPyramids();
//		try {
//			source.getThread().join();
//		} catch (InterruptedException var8) {
//			IJ.error("Unexpected interruption exception " + var8);
//		}

		final int targetHeight = imageMtx.length;
		final int targetWidth  = imageMtx[0].length;

		ImageProcessor ip = applyTransformationCoefficientsGreyscale(source, aTransform.getIntervals(),
				aTransform.getDirectDeformationCoefficientsX(), aTransform.getDirectDeformationCoefficientsY(),
				targetWidth, targetHeight);

		ByteProcessor bp = new ByteProcessor(ip,false);
		//scale input argument above doesn't work very well, so we will do it manually
		bp.setMinAndMax(0, 255);

		int[][] result = new int[ip.getHeight()][ip.getWidth()];
		extractImage(bp, result);
		return result;

	}

	/**
	 * Apply a given B-spline Transformation object to a gray-scale image.
	 *
	 * @param aTransform the Transformation to apply
	 * @param imageMtx the 2D image as a 1D array of int values, concatenated by row, should be scaled 0-255
	 *
	 * @return result transformed image as a matrix concatenated by row into a 1D array
	 */
	public static int[] applyTransformationToGreyscaleImageMtx(Transformation aTransform, int[] imageMtx,
																 int imgWidth, int imgHeight) {

		BSplineModel source = new BSplineModel(imageMtx, imgWidth, imgHeight, false);
		source.setPyramidDepth(0);
		source.startPyramids();
		try {
			source.getThread().join();
		} catch (InterruptedException var8) {
			IJ.error("Unexpected interruption exception " + var8);
		}

		final int targetHeight = imgHeight;
		final int targetWidth  = imgWidth;

		ImageProcessor ip = applyTransformationCoefficientsGreyscale(source, aTransform.getIntervals(),
				aTransform.getDirectDeformationCoefficientsX(), aTransform.getDirectDeformationCoefficientsY(),
				targetWidth, targetHeight);

		ByteProcessor bp = new ByteProcessor(ip,false);
		//scale input argument above doesn't work very well, so we will do it manually
		bp.setMinAndMax(0, 255);

		int[] result = new int[ip.getHeight() * ip.getWidth()];
		extractImage(bp, result);
		return result;

	}

	public static double[][] applyTransformationToGreyscaleImageMtx(Transformation aTransform, float[][] imageMtx) {

		BSplineModel source = new BSplineModel(imageMtx, false);
		source.setPyramidDepth(0);
		source.startPyramids();
		try {
			source.getThread().join();
		} catch (InterruptedException var8) {
			IJ.error("Unexpected interruption exception " + var8);
		}

		final int targetHeight = imageMtx.length;
		final int targetWidth  = imageMtx[0].length;

		ImageProcessor ip = applyTransformationCoefficientsGreyscale(source, aTransform.getIntervals(),
				aTransform.getDirectDeformationCoefficientsX(), aTransform.getDirectDeformationCoefficientsY(),
				targetWidth, targetHeight);

		double[][] result = new double[ip.getHeight()][ip.getWidth()];
		extractImage(ip, result);
		return result;

	}

	/* ------------------------------------------------------------------------ */
	/**
	 *  Class to apply transformation to grayscale images in a concurrent way
	 * 	 
	 */	
	private static class GrayscaleApplyTransformTile implements Runnable 
	{
		/** B-spline deformation in x */
		final BSplineModel swx;
		/** B-spline deformation in y */
		final BSplineModel swy;
		/** current source image */
		final BSplineModel source;
		/** target current width */
		final int targetCurrentWidth;
		/** target current height */
		final int targetCurrentHeight;
		/** number of intervals between B-spline coefficients */
		final int intervals;
		/** area of the image to be transformed in this thread */
		final Rectangle rect;
		/** resulting float processor containing the transformed area */
		final private FloatProcessor fp;
		
		/**
		 * Constructor for grayscale image transform 
		 * @param swx B-spline deformation in x
		 * @param swy B-spline deformation in y
		 * @param source current source image
		 * @param targetCurrentWidth target current width 
		 * @param targetCurrentHeight target current height
		 * @param intervals number of intervals between B-spline coefficients
		 * @param rect rectangle containing the area of the image to be transformed
		 * @param fp resulting float processor (output)
		 */
		GrayscaleApplyTransformTile(BSplineModel swx, 
		 		  BSplineModel swy, 
		 		  BSplineModel source,
		 		  int targetCurrentWidth,
		 		  int targetCurrentHeight,
		 		  int intervals,
				  Rectangle rect, 
				  FloatProcessor fp)
		{
			this.swx = swx;
			this.swy = swy;
			this.source = source;
			this.targetCurrentWidth = targetCurrentWidth;
			this.targetCurrentHeight = targetCurrentHeight;
			this.intervals = intervals;
			this.rect = rect;
			this.fp = fp;
		}
	
		//------------------------------------------------------------------
		/**
		 * Run method to update the intermediate window. Only the part defined by
		 * the rectangle will be updated (in this thread).
		 */
		public void run()
		{
			// Compute the warped image
			int auxTargetHeight = rect.y + rect.height;
			int auxTargetWidth = rect.x + rect.width;
			
			
			float [] fp_array = (float[]) fp.getPixels();
			
			final int sourceWidth = source.getWidth();
			final int sourceHeight = source.getHeight();
			
			
			for (int v_rect = 0, v=rect.y; v<auxTargetHeight; v++, v_rect++)
			{
				final int v_offset = v_rect * rect.width;
				//prevent NaN result from divide by 0
				final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;
				
				for (int u_rect = 0, u=rect.x; u<auxTargetWidth; u++, u_rect++) 
				{

					//prevent NaN result from divide by 0
					final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;
					final double transformation_x_v_u = swx.prepareForInterpolationAndInterpolateI(tu, tv, false, false);
					final double transformation_y_v_u = swy.prepareForInterpolationAndInterpolateI(tu, tv, false, false);
									

					final double x = transformation_x_v_u;
					final double y = transformation_y_v_u;
					
					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{
						double sval = source.prepareForInterpolationAndInterpolateI(x, y, false, false);
						fp_array[u_rect + v_offset] = (float) sval;
					}
					else
					{
						fp_array[u_rect + v_offset] = 0;
					}
				
				}
			}
		} // end run method 
		
	} // end GrayscaleApplyTransformTile class
	
	/* ------------------------------------------------------------------------ */
	/**
	 *  Class to apply transformation to color images in a concurrent way
	 * 	 
	 */	
	private static class ColorApplyTransformTile implements Runnable 
	{
		/** B-spline deformation in x */
		final BSplineModel swx;
		/** B-spline deformation in y */
		final BSplineModel swy;	
		/** red channel of the source image */
		final BSplineModel sourceR;
		/** green channel of the source image */
		final BSplineModel sourceG;
		/** blue channel of the source image */
		final BSplineModel sourceB;
		/** target current width */
		final int targetCurrentWidth;
		/** target current height */
		final int targetCurrentHeight;
		/** number of intervals between B-spline coefficients */
		final int intervals;
		/** area of the image to be transformed */
		final Rectangle rect;
		/** resulting float processor for the red channel */
		final private FloatProcessor fpR;
		/** resulting float processor for the green channel */
		final private FloatProcessor fpG;
		/** resulting float processor for the blue channel */
		final private FloatProcessor fpB;
		
		/**
		 * Constructor for color image transform 
		 * 
		 * @param swx B-spline deformation in x
		 * @param swy B-spline deformation in y
		 * @param sourceR red source image
		 * @param sourceG green source image
		 * @param sourceB blue source image
		 * @param targetCurrentWidth target current width
		 * @param targetCurrentHeight target current height
		 * @param intervals number of intervals between B-spline coefficients
		 * @param rect area of the image to be transformed
		 * @param fpR red channel processor to be updated
		 * @param fpG green channel processor to be updated
		 * @param fpB blue channel processor to be updated
		 */
		ColorApplyTransformTile(BSplineModel swx, 
		 		  BSplineModel swy, 
		 		  BSplineModel sourceR,
		 		  BSplineModel sourceG,
		 		  BSplineModel sourceB,
		 		  int targetCurrentWidth,
		 		  int targetCurrentHeight,
		 		  int intervals,
				  Rectangle rect, 
				  FloatProcessor fpR,
				  FloatProcessor fpG,
				  FloatProcessor fpB)
		{
			this.swx = swx;
			this.swy = swy;
			this.sourceR = sourceR;
			this.sourceG = sourceG;
			this.sourceB = sourceB;
			this.targetCurrentWidth = targetCurrentWidth;
			this.targetCurrentHeight = targetCurrentHeight;
			this.intervals = intervals;
			this.rect = rect;
			this.fpR = fpR;
			this.fpG = fpG;
			this.fpB = fpB;
		}
	
		//------------------------------------------------------------------
		/**
		 * Run method to update the intermediate window. Only the part defined by
		 * the rectangle will be updated (in this thread).
		 */
		public void run()
		{
			// Compute the warped image
			int auxTargetHeight = rect.y + rect.height;
			int auxTargetWidth = rect.x + rect.width;
			
			
			float [] fpR_array = (float[]) fpR.getPixels();
			float [] fpG_array = (float[]) fpG.getPixels();
			float [] fpB_array = (float[]) fpB.getPixels();
			
			
			final int sourceWidth = sourceR.getWidth();
			final int sourceHeight = sourceR.getHeight();
			
			
			for (int v_rect = 0, v=rect.y; v<auxTargetHeight; v++, v_rect++)
			{
				final int v_offset = v_rect * rect.width;
				//prevent NaN result from divide by 0
				final double tv = (targetCurrentHeight > 1) ? ((double)(v * intervals) / (double)(targetCurrentHeight - 1) + 1.0F) : 1.0F;
				
				for (int u_rect = 0, u=rect.x; u<auxTargetWidth; u++, u_rect++) 
				{
					//prevent NaN result from divide by 0
					final double tu = (targetCurrentWidth > 1) ? ((double)(u * intervals) / (double)(targetCurrentWidth - 1) + 1.0F) : 1.0F;
					
					final double x = swx.prepareForInterpolationAndInterpolateI(tu, tv, false, false);
					final double y = swy.prepareForInterpolationAndInterpolateI(tu, tv, false, false);														
					
					if (x>=0 && x<sourceWidth && y>=0 && y<sourceHeight)
					{
						fpR_array[u_rect + v_offset] = (float) (sourceR.prepareForInterpolationAndInterpolateI(x, y, false, false));						
						fpG_array[u_rect + v_offset] = (float) (sourceG.prepareForInterpolationAndInterpolateI(x, y, false, false));						
						fpB_array[u_rect + v_offset] = (float) (sourceB.prepareForInterpolationAndInterpolateI(x, y, false, false));						
					}
					else
					{
						fpR_array[u_rect + v_offset] = 0;
						fpG_array[u_rect + v_offset] = 0;
						fpB_array[u_rect + v_offset] = 0;						
					}
				
				}
			}
			
			
			
		} // end run method 
		
	} // end ColorApplyTransformTile class

	//------------------------------------------------------------------
	/**
	 * Smooth with a Gaussian kernel that represents downsampling at a given
	 * scale factor and sourceSigma.
	 */
	final static public void smoothForScale(
		final ImageProcessor source,
		final float scale,
		final float sourceSigma,
		final float targetSigma )
	{
		if ( scale >= 1.0f ) return;
		float s = targetSigma / scale;
		float sigma = ( float )Math.sqrt( s * s - sourceSigma * sourceSigma );
		new GaussianBlur().blurGaussian( source, sigma, sigma, 0.01 );
	}

	//------------------------------------------------------------------
	/**
	 * Create a downsampled ImageProcessor.
	 * 
	 * @param source the source image
	 * @param scale scaling factor
	 * @param sourceSigma the Gaussian at which the source was sampled (guess 0.5 if you do not know)
	 * @param targetSigma the Gaussian at which the target will be sampled
	 * 
	 * @return a new {@link FloatProcessor}
	 */
	final static public ImageProcessor createDownsampled(
			final ImageProcessor source,
			final float scale,
			final float sourceSigma,
			final float targetSigma )
	{
		final int ow = source.getWidth();
		final int oh = source.getHeight();
		final int w = Math.round( ow * scale );
		final int h = Math.round( oh * scale );
		
		final ImageProcessor temp = source.duplicate();
		if ( scale >= 1.0f ) return temp;
			
		smoothForScale( temp, scale, sourceSigma, targetSigma );
		
		return temp.resize( w, h );
	}
	
	//------------------------------------------------------------------
	/**
	 * Scale an image with good quality in both up and down direction
	 */
	final static public ImageProcessor scale(
			final ImageProcessor source,
			final float scale )
	{
		if ( scale == 1.0f ) return source.duplicate();
		else if ( scale < 1.0f ) return createDownsampled( source, scale, 0.5f, 0.5f );
		else
		{
			source.setInterpolationMethod( ImageProcessor.BILINEAR );
			return source.resize( Math.round( scale * source.getWidth() ) );
		}
	}
	
	/**
	 * Apply transform saved on a file to all slices of moving image
	 * 
	 * @param transformationFile elastic transform file
	 * @param fixedImage fixed image
	 * @param movingStack stack containing all the moving images
	 * @return stack containing the deformed slices
	 */
	public static ImagePlus applyTransformToStack(
			String transformationFile,
			ImagePlus fixedImage,
			ImagePlus movingStack )
	{

		// read number of intervals from transformation file
		final int intervals = 
			MiscTools.numberOfIntervalsOfTransformation( transformationFile );

		// declare arrays of coefficients
		final double[][] cx = new double[ intervals + 3 ][ intervals + 3 ];
		final double[][] cy = new double[ intervals + 3 ][ intervals + 3 ];

		// read coefficients
		MiscTools.loadTransformation( transformationFile, cx, cy );

		ImageStack outputStack = 
			new ImageStack( movingStack.getWidth(), movingStack.getHeight() );

		// apply transform to each slice of the stack

		for( int i=1; i<=movingStack.getImageStackSize(); i++ )
		{
			ImageProcessor ip = movingStack.getImageStack().getProcessor( i );

			BSplineModel source = new BSplineModel( ip, false, 1 );

			ImagePlus movingImage = new ImagePlus("", ip );			
				
			ImageProcessor result = 
					MiscTools.applyTransformationMT( 
						movingImage, fixedImage, source, intervals, cx, cy );

			outputStack.addSlice( "", result );
		}

		// return results
		return new ImagePlus( 
				movingStack.getTitle() +"-deformed", outputStack );
	}

	/**
	 * Calculate image similarity between two images (target and source)
	 * @param targetImp target image
	 * @param sourceImp source image
	 * @param targetMsk target mask
	 * @param verbose flag to display result in log window
	 * @return image similarity value or -1 if error
	 */
	public static double imageSimilarity(
			ImagePlus targetImp,
			ImagePlus sourceImp,
			Mask targetMsk,
			boolean verbose )
	{
		// Source image
		int k=0;
		ImageProcessor sourceIp = sourceImp.getProcessor();

		int sourceHeight = sourceIp.getHeight();
		int sourceWidth  = sourceIp.getWidth ();

		// Target image
		ImageProcessor targetIp = targetImp.getProcessor();

		int targetHeight = targetIp.getHeight();
		int targetWidth  = targetIp.getWidth ();

		if(sourceHeight != targetHeight || sourceWidth != targetWidth)
		{
			IJ.error("Error: source and target dimensions do not match!");
			return -1;
		}

		// Read source pixel values.
		double [] sourceImage = new double[sourceHeight * sourceWidth];

		if (sourceIp instanceof ByteProcessor)
		{
			final byte[] pixels = (byte[])sourceIp.getPixels();
			for (int y = 0; (y < sourceHeight); y++)
				for (int x = 0; (x < sourceWidth); x++, k++)
					sourceImage[k] = (double)(pixels[k] & 0xFF);
		}
		else if (sourceIp instanceof ShortProcessor)
		{
			final short[] pixels = (short[])sourceIp.getPixels();
			for (int y = 0; (y < sourceHeight); y++)
				for (int x = 0; (x < sourceWidth); x++, k++)
					if (pixels[k] < (short)0) sourceImage[k] = (double)pixels[k] + 65536.0F;
					else                      sourceImage[k] = (double)pixels[k];
		}
		else if (sourceIp instanceof FloatProcessor)
		{
			final float[] pixels = (float[])sourceIp.getPixels();
			for (int p = 0; p<sourceHeight*sourceWidth; p++)
				sourceImage[p]=pixels[p];
		}
		else if (sourceIp instanceof ColorProcessor)
		{
			ImageProcessor fp = sourceIp.convertToFloat();
			final float[] pixels = (float[])fp.getPixels();
			for (int p = 0; p<sourceHeight*sourceWidth; p++)
				sourceImage[p] = pixels[p];
		}

		// Read target pixel values.
		k=0;

		double [] targetImage = new double[targetHeight * targetWidth];

		if (targetIp instanceof ByteProcessor)
		{
			final byte[] pixels = (byte[])targetIp.getPixels();
			for (int y = 0; (y < targetHeight); y++)
				for (int x = 0; (x < targetWidth); x++, k++)
					targetImage[k] = (double)(pixels[k] & 0xFF);
		}
		else if (targetIp instanceof ShortProcessor)
		{
			final short[] pixels = (short[])targetIp.getPixels();
			for (int y = 0; (y < targetHeight); y++)
				for (int x = 0; (x < targetWidth); x++, k++)
					if (pixels[k] < (short)0) targetImage[k] = (double)pixels[k] + 65536.0F;
					else                      targetImage[k] = (double)pixels[k];
		}
		else if (targetIp instanceof FloatProcessor)
		{
			final float[] pixels = (float[])targetIp.getPixels();
			for (int p = 0; p<targetHeight*targetWidth; p++)
				targetImage[p]=pixels[p];
		}
		else if (targetIp instanceof ColorProcessor)
		{
			ImageProcessor fp = targetIp.convertToFloat();
			final float[] pixels = (float[])fp.getPixels();
			for (int p = 0; p<targetHeight*targetWidth; p++)
				targetImage[p] = pixels[p];
		}

		double imageSimilarity = 0;
		int n = 0;

		for (int v=0; v < targetImp.getHeight(); v++)
		{
			for (int u=0; u<targetImp.getWidth(); u++)
			{
				if ( null == targetMsk || targetMsk.getValue( u, v ) )
				{
					// Compute image term ......................
					double I2 = targetImage[v*targetWidth + u];
					double I1 = sourceImage[v*targetWidth + u];


					double error = I2 - I1;
					double error2 = error*error;

					imageSimilarity += error2;
					n++;
				}
			}
		}

		if(n != 0)
		{
			if( verbose )
				IJ.log( " Image similarity = " + (imageSimilarity / n)
						+ ", n = " + n );
			return imageSimilarity / n;
		}
		else
		{
			if( verbose )
				IJ.log( " Error: not a single pixel was evaluated " );
			return -1;
		}
	}
	/**
	 * Save source and target landmarks to file
	 * @param outputPath complete output landmark path
	 * @param sourceList vector of source landmark points
	 * @param targetList vector of target landmark points
	 */
	public static void saveLandmarks(
			final String outputPath,
			final Vector <Point> sourceList,
			final Vector <Point> targetList )
	{
		if ( null == outputPath ) {
			return;
		}
		try {
			final FileWriter fw = new FileWriter( outputPath );
			Point sourcePoint;
			Point targetPoint;
			String n;
			String xSource;
			String ySource;
			String xTarget;
			String yTarget;
			fw.write("Index\txSource\tySource\txTarget\tyTarget\n");
			for (int k = 0; (k < sourceList.size()); k++) {
				n = "" + k;
				while (n.length() < 5) {
					n = " " + n;
				}
				sourcePoint = (Point)sourceList.elementAt(k);
				xSource = "" + sourcePoint.x;
				while (xSource.length() < 7) {
					xSource = " " + xSource;
				}
				ySource = "" + sourcePoint.y;
				while (ySource.length() < 7) {
					ySource = " " + ySource;
				}
				targetPoint = (Point)targetList.elementAt(k);
				xTarget = "" + targetPoint.x;
				while (xTarget.length() < 7) {
					xTarget = " " + xTarget;
				}
				yTarget = "" + targetPoint.y;
				while (yTarget.length() < 7) {
					yTarget = " " + yTarget;
				}
				fw.write(n + "\t" + xSource + "\t" + ySource + "\t" + xTarget +
						"\t" + yTarget + "\n");
			}
			fw.close();
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
		} catch (SecurityException e) {
			IJ.error("Security exception" + e);
		}
	}
	/**
	 * Display landmark points in a separate table
	 * @param sourceList vector of source landmark points
	 * @param targetList vector of target landmark points
	 */
	public static void showPoints(
			final Vector<Point> sourceList,
			final Vector<Point> targetList )
	{
		Point sourcePoint;
		Point targetPoint;
		String n;
		String xTarget;
		String yTarget;
		String xSource;
		String ySource;
		IJ.getTextPanel().setFont(new Font("Monospaced", Font.PLAIN, 12));
		IJ.setColumnHeadings("Index\txSource\tySource\txTarget\tyTarget");
		for (int k = 0; (k < sourceList.size()); k++) {
			n = "" + k;
			while (n.length() < 5) {
				n = " " + n;
			}
			sourcePoint = (Point)sourceList.elementAt(k);
			xTarget = "" + sourcePoint.x;
			while (xTarget.length() < 7) {
				xTarget = " " + xTarget;
			}
			yTarget = "" + sourcePoint.y;
			while (yTarget.length() < 7) {
				yTarget = " " + yTarget;
			}
			targetPoint = (Point)targetList.elementAt(k);
			xSource = "" + targetPoint.x;
			while (xSource.length() < 7) {
				xSource = " " + xSource;
			}
			ySource = "" + targetPoint.y;
			while (ySource.length() < 7) {
				ySource = " " + ySource;
			}
			IJ.getTextPanel().append( n + "\t" + xSource + "\t" + ySource +
					"\t" + xTarget + "\t" + yTarget);
		}
	}
	/**
	 * Show a non-recordable dialog so the user selects a file.
	 * @param dialogTitle displayed dialog title
	 * @param saveDialog flag to create save (if true) or load dialog
	 * @return complete path to file or null if dialog is cancelled.
	 */
	public static String getUserSelectedFilePath(
			String dialogTitle,
			boolean saveDialog )
	{
		String path = null;
		String filename = null;
		String currentFolder = OpenDialog.getLastDirectory() != null ?
				OpenDialog.getLastDirectory() : OpenDialog.getDefaultDirectory();
		if( Prefs.useFileChooser )
		{
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle( dialogTitle );
			chooser.setCurrentDirectory( new File( currentFolder ));
			chooser.setVisible( true );
			chooser.setDialogType( saveDialog ? JFileChooser.SAVE_DIALOG :
				JFileChooser.OPEN_DIALOG );
			final int result = chooser.showOpenDialog( null );
			if( result == JFileChooser.APPROVE_OPTION )
			{
				path = chooser.getCurrentDirectory().getPath() + File.separator;
				filename = chooser.getSelectedFile().getName();
			}
		}
		else
		{
			FileDialog fd = new FileDialog( IJ.getInstance(),
					dialogTitle, saveDialog ? FileDialog.SAVE : FileDialog.LOAD );
			fd.setDirectory( currentFolder );
			fd.setVisible( true );
			path = fd.getDirectory() + File.separator;
			filename = fd.getName();
		}

		if ((path == null) || (filename == null))
			return null;
		return path+filename;
	}
	/**
	 * Calculate warping index between an elastic and a raw transform loaded
	 * from file.
	 *
	 * @param elasticTransfPath complete path of elastic transform file
	 * @param rawTransfPath complete path of raw transform file
	 * @param targetImp target image
	 * @param sourceImp source image
	 * @return warping index
	 */
	public static double elasticRawWarpingIndex(
			String elasticTransfPath,
			String rawTransfPath,
			ImagePlus targetImp,
			ImagePlus sourceImp )
	{
		int intervals = MiscTools.numberOfIntervalsOfTransformation(elasticTransfPath);

		double [][]cx_direct = new double[intervals+3][intervals+3];
		double [][]cy_direct = new double[intervals+3][intervals+3];

		MiscTools.loadTransformation(elasticTransfPath, cx_direct, cy_direct);

		// We load the transformation raw file.
		double[][] transformation_x = new double[targetImp.getHeight()][targetImp.getWidth()];
		double[][] transformation_y = new double[targetImp.getHeight()][targetImp.getWidth()];
		MiscTools.loadRawTransformation(rawTransfPath, transformation_x,
				transformation_y);

		double warpingIndex = MiscTools.rawWarpingIndex( sourceImp, targetImp,
				intervals, cx_direct, cy_direct, transformation_x,
				transformation_y);
		return warpingIndex;
	}
	/**
	 * Calculate warping index between two raw transforms loaded from file.
	 * Both transforms have the same direction.
	 *
	 * @param rawTransfPath1 complete path of first raw transform file
	 * @param rawTransfPath2 complete path of second raw transform file
	 * @param targetImp target image
	 * @param sourceImp source image
	 * @return warping index
	 */
	public static double rawWarpingIndex(
			String rawTransfPath1,
			String rawTransfPath2,
			ImagePlus targetImp,
			ImagePlus sourceImp )
	{
		final double [][]transformation_x1 =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];
		final double [][]transformation_y1 =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];

		MiscTools.loadRawTransformation( rawTransfPath1,
				transformation_x1, transformation_y1 );

		final double [][]transformation_x2 =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];
		final double [][]transformation_y2 =
				new double[ targetImp.getHeight()][ targetImp.getWidth() ];

		MiscTools.loadRawTransformation( rawTransfPath2,
				transformation_x2, transformation_y2 );

		// Now we compare both transformations through the "warping index",
		double warpingIndex = MiscTools.rawWarpingIndex( sourceImp,
				targetImp, transformation_x1, transformation_y1,
				transformation_x2, transformation_y2 );
		return warpingIndex;
	}
	/**
	 * Compose a raw and an elastic transform and save result into file.
	 *
	 * @param rawTransfPath complete path to raw transform
	 * @param elasticTransfPath complete path to elastic transform
	 * @param outputPath complete output file path
	 * @param targetImp target image
	 * @param sourceImp source image
	 */
	public static void composeRawElasticTransforms(
			final String rawTransfPath,
			final String elasticTransfPath,
			final String outputPath,
			final ImagePlus targetImp,
			final ImagePlus sourceImp )
	{
		double[][] transformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double[][] transformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		MiscTools.loadRawTransformation(
				rawTransfPath, transformation_x, transformation_y );

		int intervals =
				MiscTools.numberOfIntervalsOfTransformation( elasticTransfPath );

		double [][]cx = new double[ intervals+3 ][ intervals+3 ];
		double [][]cy = new double[ intervals+3 ][ intervals+3 ];

		MiscTools.loadTransformation( elasticTransfPath, cx, cy );

		double [][] outputTransformation_x =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];
		double [][] outputTransformation_y =
				new double[ targetImp.getHeight() ][ targetImp.getWidth() ];

		// Now we compose them and get as result a raw transformation mapping.
		MiscTools.composeRawElasticTransformations( targetImp, intervals,
				transformation_x, transformation_y, cx, cy,
				outputTransformation_x, outputTransformation_y);

		MiscTools.saveRawTransformation( outputPath, targetImp.getWidth(),
				targetImp.getHeight(), outputTransformation_x,
				outputTransformation_y );
	}

} /* End of MiscTools class */
