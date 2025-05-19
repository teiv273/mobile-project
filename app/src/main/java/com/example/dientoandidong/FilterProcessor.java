package com.example.dientoandidong;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ImageFilters {

    public static Bitmap applySketchFilter(Bitmap input) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        Mat inv = new Mat();
        Core.bitwise_not(gray, inv);
        Mat blur = new Mat();
        Imgproc.GaussianBlur(inv, blur, new Size(21, 21), 0);
        Mat white = new Mat(blur.size(), blur.type(), new Scalar(255));
        Mat diff = new Mat();
        Core.subtract(white, blur, diff);
        Mat blend = new Mat();
        Core.divide(gray, diff, blend, 256.0);
        Imgproc.cvtColor(blend, blend, Imgproc.COLOR_GRAY2BGR); // Chuyển lại BGR để hiển thị
        Bitmap result = Bitmap.createBitmap(blend.cols(), blend.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(blend, result);
        // Release Mats
        img.release();
        gray.release();
        inv.release();
        blur.release();
        white.release();
        diff.release();
        blend.release();
        return result;
    }

    public static Bitmap applyEmbossFilter(Bitmap input) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);
        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        kernel.put(0, 0, -2, -1, 0, -1, 1, 1, 0, 1, 2);
        Mat output = new Mat();
        Imgproc.filter2D(img, output, -1, kernel);
        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        // Release Mats
        img.release();
        kernel.release();
        output.release();
        return result;
    }

    public static Bitmap applyGrayscaleFilter(Bitmap input, float intensity) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(input, originalMat);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(grayMat, grayMat, Imgproc.COLOR_GRAY2BGR); // Chuyển lại BGR để addWeighted

        Mat resultMat = new Mat();
        Core.addWeighted(grayMat, intensity, originalMat, 1.0 - intensity, 0.0, resultMat);

        Bitmap result = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultMat, result);

        originalMat.release();
        grayMat.release();
        resultMat.release();
        return result;
    }

    public static Bitmap applySepiaFilter(Bitmap input, float intensity) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(input, originalMat);

        Mat img = originalMat.clone();
        if (img.channels() == 4) Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        else if (img.channels() == 1) Imgproc.cvtColor(img, img, Imgproc.COLOR_GRAY2BGR);

        Mat sepiaKernel = new Mat(3, 3, CvType.CV_32F);
        sepiaKernel.put(0, 0, 0.272f, 0.534f, 0.131f);
        sepiaKernel.put(1, 0, 0.349f, 0.686f, 0.168f);
        sepiaKernel.put(2, 0, 0.393f, 0.769f, 0.189f);

        Mat fullSepiaMat = new Mat();
        Core.transform(img, fullSepiaMat, sepiaKernel);
        fullSepiaMat.convertTo(fullSepiaMat, CvType.CV_8UC3);

        Mat resultMat = new Mat();
        Core.addWeighted(fullSepiaMat, intensity, originalMat, 1.0 - intensity, 0.0, resultMat);

        Bitmap result = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultMat, result);

        originalMat.release();
        img.release();
        sepiaKernel.release();
        fullSepiaMat.release();
        resultMat.release();
        return result;
    }

    public static Bitmap applyInvertFilter(Bitmap input, boolean invertR, boolean invertG, boolean invertB) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);

        if (img.channels() == 1) {
            if (invertR || invertG || invertB) { // Invert if any channel is selected for grayscale
                Core.bitwise_not(img, img);
            }
        } else if (img.channels() >= 3) {
            ArrayList<Mat> channels = new ArrayList<>(img.channels());
            Core.split(img, channels);

            if (invertB && !channels.isEmpty()) Core.bitwise_not(channels.get(0), channels.get(0));
            if (invertG && channels.size() > 1) Core.bitwise_not(channels.get(1), channels.get(1));
            if (invertR && channels.size() > 2) Core.bitwise_not(channels.get(2), channels.get(2));

            Core.merge(channels, img);
            for(Mat channel : channels) channel.release();
        }

        Bitmap result = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, result);
        img.release();
        return result;
    }

    public static Bitmap applyVignetteFilter(Bitmap input, float radius, float strength) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);
        if (img.channels() == 4) Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        img.convertTo(img, CvType.CV_32F, 1.0 / 255.0); // Normalize

        int width = img.cols();
        int height = img.rows();
        Point center = new Point(width / 2.0, height / 2.0);

        Mat mask = new Mat(height, width, CvType.CV_32F);
        double maxDist = Math.sqrt(center.x * center.x + center.y * center.y);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                double dist = Math.sqrt(Math.pow(j - center.x, 2) + Math.pow(i - center.y, 2));
                double norm = dist / (maxDist * radius); // radius: 0 to 1, smaller means tighter vignette
                float value = (float) Math.pow(1.0 - Math.min(1.0, norm), strength);
                mask.put(i, j, value);
            }
        }

        Imgproc.GaussianBlur(mask, mask, new Size(151, 151), 0); // Large blur for smooth falloff

        List<Mat> maskChannels = new ArrayList<>();
        for (int i = 0; i < 3; i++) maskChannels.add(mask); // Create 3-channel mask
        Mat mask3Channel = new Mat();
        Core.merge(maskChannels, mask3Channel);

        Mat resultMat = new Mat();
        Core.multiply(img, mask3Channel, resultMat); // Apply mask

        resultMat.convertTo(resultMat, CvType.CV_8UC3, 255.0); // Convert back to 8-bit

        Bitmap resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultMat, resultBitmap);

        img.release();
        mask.release();
        for(Mat mc : maskChannels) mc.release(); // release individual channel mats
        mask3Channel.release();
        resultMat.release();
        return resultBitmap;
    }

    public enum BlockShape {
        SQUARE, TRIANGLE, HEXAGON
    }

    public static Bitmap applyPixelateFilter(Bitmap inputBitmap, int blockSize, BlockShape shape) {
        Mat src = new Mat();
        Utils.bitmapToMat(inputBitmap, src);
        if (src.channels() == 4) Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB);

        Mat output = Mat.zeros(src.size(), src.type());

        if (shape == BlockShape.SQUARE) {
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);
                    Rect rect = new Rect(x, y, width, height);
                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);
                    Imgproc.rectangle(output, new Point(x, y), new Point(x + width, y + height), color, -1);
                    block.release();
                }
            }
        } else if (shape == BlockShape.TRIANGLE) {
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);
                    if (width <= 0 || height <= 0) continue;
                    Rect rect = new Rect(x, y, width, height);
                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);
                    Point[] triangle1 = new Point[]{ new Point(x, y), new Point(x + width, y), new Point(x + width / 2.0, y + height) };
                    Point[] triangle2 = new Point[]{ new Point(x, y + height), new Point(x + width, y + height), new Point(x + width / 2.0, y) };
                    if (inImageBounds(triangle1, src.cols(), src.rows())) Imgproc.fillConvexPoly(output, new MatOfPoint(triangle1), color);
                    if (inImageBounds(triangle2, src.cols(), src.rows())) Imgproc.fillConvexPoly(output, new MatOfPoint(triangle2), color);
                    block.release();
                }
            }
        } else if (shape == BlockShape.HEXAGON) {
            double hexHeightFactor = blockSize; // Treat blockSize as a factor for hexagon size
            double hexRadius = hexHeightFactor / 2.0; // Effective radius of the hexagon
            double hexWidth = Math.sqrt(3) * hexRadius; // Width of hexagon
            double hexHeight = 2 * hexRadius; // Full height of hexagon

            double vertSpacing = hexHeight * 0.75; // Vertical distance between centers of rows

            for (int row = 0; ; ++row) {
                double currentY = row * vertSpacing;
                if (currentY - hexRadius > src.rows()) break; // Stop if hexagon is completely out of image

                for (int col = 0; ; ++col) {
                    // Offset every other row for staggered layout
                    double currentX = col * hexWidth + (row % 2 == 1 ? hexWidth / 2.0 : 0);
                    if (currentX - hexRadius > src.cols()) break; // Stop if hexagon is completely out of image

                    Point[] hexagonPoints = new Point[6];
                    for (int i = 0; i < 6; i++) {
                        double angle_deg = 60 * i; // Hexagon vertices are 60 degrees apart
                        double angle_rad = Math.toRadians(angle_deg);
                        hexagonPoints[i] = new Point(
                                currentX + hexRadius * Math.cos(angle_rad),
                                currentY + hexRadius * Math.sin(angle_rad)
                        );
                    }

                    // Define a bounding box for the hexagon to get the submat
                    Rect roiBounding = Imgproc.boundingRect(new MatOfPoint(hexagonPoints));
                    // Ensure ROI is within image bounds
                    int roiX = Math.max(0, roiBounding.x);
                    int roiY = Math.max(0, roiBounding.y);
                    int roiW = Math.min(roiBounding.width, src.cols() - roiX);
                    int roiH = Math.min(roiBounding.height, src.rows() - roiY);

                    if (roiW <= 0 || roiH <= 0) continue; // Skip if ROI is invalid

                    Mat block = src.submat(new Rect(roiX, roiY, roiW, roiH));
                    Scalar color = Core.mean(block); // Get average color of the area
                    Imgproc.fillConvexPoly(output, new MatOfPoint(hexagonPoints), color);
                    block.release();
                }
            }
        }

        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        src.release();
        output.release();
        return result;
    }

    private static boolean inImageBounds(Point[] points, int width, int height) {
        for (Point p : points) {
            // Points should be >= 0 and < width/height
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height) return false;
        }
        return true;
    }
}