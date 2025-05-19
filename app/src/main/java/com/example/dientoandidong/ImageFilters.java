package com.example.dientoandidong;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

        // Tạo một ma trận 255 cùng kích thước với blur
        Mat white = new Mat(blur.size(), blur.type(), new Scalar(255));

        // subtract(white - blur)
        Mat diff = new Mat();
        Core.subtract(white, blur, diff);

        // gray / (255 - blur)
        Mat blend = new Mat();
        Core.divide(gray, diff, blend, 256.0);


        Imgproc.cvtColor(blend, blend, Imgproc.COLOR_GRAY2BGR);

        Bitmap result = Bitmap.createBitmap(blend.cols(), blend.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(blend, result);
        return result;
    }

    public static Bitmap applyEmbossFilter(Bitmap input) {
        Mat img = new Mat();
        Utils.bitmapToMat(input, img);

        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        kernel.put(0, 0,
                -2, -1, 0,
                -1, 1, 1,
                0, 1, 2
        );

        Mat output = new Mat();
        Imgproc.filter2D(img, output, -1, kernel);

        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        return result;
    }

    public enum BlockShape {
        SQUARE, TRIANGLE, HEXAGON
    }

    public static Bitmap applyPixelateFilter(Bitmap inputBitmap, int blockSize, BlockShape shape) {
        // Validate parameters
        if (blockSize <= 0) blockSize = 10; // Use safe default

        Mat src = new Mat();
        Utils.bitmapToMat(inputBitmap, src);
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB);

        Mat output = Mat.zeros(src.size(), src.type());

        // SQUARE pixelation
        if (shape == BlockShape.SQUARE) {
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);
                    Rect rect = new Rect(x, y, width, height);
                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);
                    Imgproc.rectangle(output, new Point(x, y), new Point(x + width, y + height), color, -1);
                }
            }
        }
        // TRIANGLE pixelation
        else if (shape == BlockShape.TRIANGLE) {
            // Create a grid of triangles to completely cover the image
            for (int y = 0; y < src.rows(); y += blockSize) {
                for (int x = 0; x < src.cols(); x += blockSize) {
                    // Ensure we don't go beyond the image boundaries
                    int width = Math.min(blockSize, src.cols() - x);
                    int height = Math.min(blockSize, src.rows() - y);

                    // Each square is divided into two triangles

                    // Define upper-left triangle vertices
                    Point[] triangle1 = new Point[3];
                    triangle1[0] = new Point(x, y);
                    triangle1[1] = new Point(x + width, y);
                    triangle1[2] = new Point(x, y + height);

                    // Define lower-right triangle vertices
                    Point[] triangle2 = new Point[3];
                    triangle2[0] = new Point(x + width, y);
                    triangle2[1] = new Point(x + width, y + height);
                    triangle2[2] = new Point(x, y + height);

                    // Sample color for the first triangle from its region
                    Rect rect1 = new Rect(x, y, width / 2, height / 2);
                    if (rect1.width > 0 && rect1.height > 0) {
                        Mat block1 = src.submat(rect1);
                        Scalar color1 = Core.mean(block1);

                        // Draw the first triangle
                        MatOfPoint triangleContour1 = new MatOfPoint(triangle1);
                        Imgproc.fillConvexPoly(output, triangleContour1, color1);
                    }

                    // Sample color for the second triangle from its region
                    Rect rect2 = new Rect(x + width / 2, y + height / 2, width / 2, height / 2);
                    if (rect2.x + rect2.width <= src.cols() && rect2.y + rect2.height <= src.rows()
                            && rect2.width > 0 && rect2.height > 0) {
                        Mat block2 = src.submat(rect2);
                        Scalar color2 = Core.mean(block2);

                        // Draw the second triangle
                        MatOfPoint triangleContour2 = new MatOfPoint(triangle2);
                        Imgproc.fillConvexPoly(output, triangleContour2, color2);
                    }
                }
            }
        }
        // HEXAGON pixelation
        else if (shape == BlockShape.HEXAGON) {
            // Better hexagon proportions
            double hexRadius = blockSize / 2.0;
            double hexHeight = hexRadius * 2;
            double hexWidth = hexRadius * Math.sqrt(3);

            // Row offset for better hexagon tiling
            double vOffset = hexRadius * 1.5;
            double hOffset = hexWidth;

            for (int row = 0; row < Math.ceil(src.rows() / vOffset) + 1; row++) {
                double y = row * vOffset;
                double x_offset = (row % 2 == 0) ? 0 : hexWidth / 2;

                for (double x = x_offset; x < src.cols() + hexWidth; x += hexWidth) {
                    // Create hexagon vertices
                    Point[] hexagon = new Point[6];
                    for (int i = 0; i < 6; i++) {
                        double angle_rad = Math.PI / 180 * (60 * i - 30);
                        hexagon[i] = new Point(
                                x + hexRadius * Math.cos(angle_rad),
                                y + hexRadius * Math.sin(angle_rad)
                        );
                    }

                    // Calculate bounding box
                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

                    for (Point p : hexagon) {
                        minX = (int) Math.min(minX, p.x);
                        minY = (int) Math.min(minY, p.y);
                        maxX = (int) Math.max(maxX, p.x);
                        maxY = (int) Math.max(maxY, p.y);
                    }

                    // Clip to image bounds
                    minX = Math.max(0, minX);
                    minY = Math.max(0, minY);
                    maxX = Math.min(src.cols() - 1, maxX);
                    maxY = Math.min(src.rows() - 1, maxY);

                    // Skip if the hexagon is completely outside the image
                    if (minX >= maxX || minY >= maxY || minX < 0 || minY < 0 || maxX >= src.cols() || maxY >= src.rows()) {
                        continue;
                    }

                    // Sample color from region
                    Rect rect = new Rect(minX, minY, maxX - minX + 1, maxY - minY + 1);
                    Mat block = src.submat(rect);
                    Scalar color = Core.mean(block);

                    // Create points within image bounds
                    Point[] clippedHexagon = new Point[6];
                    for (int i = 0; i < 6; i++) {
                        clippedHexagon[i] = new Point(
                                Math.max(0, Math.min(src.cols() - 1, hexagon[i].x)),
                                Math.max(0, Math.min(src.rows() - 1, hexagon[i].y))
                        );
                    }

                    // Draw hexagon
                    MatOfPoint hexContour = new MatOfPoint(clippedHexagon);
                    Imgproc.fillConvexPoly(output, hexContour, color);
                }
            }
        }

        // Release temporary matrices to avoid memory leaks
        src.release();

        // Convert result back to Bitmap
        Bitmap result = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, result);
        output.release();

        return result;
    }


    private static boolean inImageBounds(Point[] points, int width, int height) {
        for (Point p : points) {
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height) return false;
        }
        return true;
    }


    public static Bitmap applyOilPaintFilter(Bitmap inputBitmap, int radius, int intensityLevels) {
        // Validate parameters
        if (radius <= 0) radius = 5; // Default radius
        if (intensityLevels <= 0) intensityLevels = 20; // Default intensity levels

        // Convert Bitmap to Mat
        Mat src = new Mat();
        Utils.bitmapToMat(inputBitmap, src);
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB);

        // Create grayscale version for intensity calculation
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY);

        // Output matrix
        Mat result = src.clone();

        // For smoother edges, we'll use a larger kernel
        int kernelSize = radius * 2 + 1;

        // First, apply a slight blur to reduce noise (optional)
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(src, blurred, new Size(3, 3), 0);

        // Define how many channels our source image has
        int channels = src.channels();

        // Process each pixel
        for (int y = radius; y < src.rows() - radius; y++) {
            for (int x = radius; x < src.cols() - radius; x++) {
                // Create bins for each intensity level
                int[] intensityCount = new int[intensityLevels];
                double[][] avgColor = new double[intensityLevels][channels];

                // Process the neighborhood
                for (int ny = y - radius; ny <= y + radius; ny++) {
                    for (int nx = x - radius; nx <= x + radius; nx++) {
                        // Get intensity (grayscale value)
                        double intensity = gray.get(ny, nx)[0];

                        // Calculate which intensity bin this belongs to
                        int intensityBin = (int)(intensity * intensityLevels / 256);
                        intensityBin = Math.min(intensityLevels - 1, Math.max(0, intensityBin));

                        // Get color
                        double[] color = src.get(ny, nx);

                        // Update bin
                        intensityCount[intensityBin]++;
                        for (int c = 0; c < channels; c++) {
                            avgColor[intensityBin][c] += color[c];
                        }
                    }
                }

                // Find most frequent intensity level
                int maxCount = 0;
                int maxIndex = 0;
                for (int i = 0; i < intensityLevels; i++) {
                    if (intensityCount[i] > maxCount) {
                        maxCount = intensityCount[i];
                        maxIndex = i;
                    }
                }

                // Calculate average color for the most common intensity level
                double[] newColor = new double[channels];
                if (intensityCount[maxIndex] > 0) {
                    for (int c = 0; c < channels; c++) {
                        newColor[c] = avgColor[maxIndex][c] / intensityCount[maxIndex];
                    }
                    result.put(y, x, newColor);
                }
            }
        }

        // Add some artistic detail by enhancing edges slightly
        Mat edges = new Mat();
        Imgproc.Laplacian(gray, edges, CvType.CV_8U, 3);
        Core.multiply(edges, new Scalar(0.2), edges); // Reduce edge influence

        // Blend edges with the oil painted result for artistic effect
        for (int y = 0; y < result.rows(); y++) {
            for (int x = 0; x < result.cols(); x++) {
                if (y < radius || y >= result.rows() - radius ||
                        x < radius || x >= result.cols() - radius) {
                    continue; // Skip border pixels
                }

                double[] color = result.get(y, x);
                double edgeValue = edges.get(y, x)[0] / 255.0;

                // Add slight edge enhancement
                for (int c = 0; c < channels; c++) {
                    color[c] = Math.min(255, Math.max(0, color[c] - edgeValue * 15));
                }

                result.put(y, x, color);
            }
        }

        // Release resources
        src.release();
        gray.release();
        blurred.release();
        edges.release();

        // Convert result back to Bitmap
        Bitmap resultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, resultBitmap);
        result.release();

        return resultBitmap;
    }


}


