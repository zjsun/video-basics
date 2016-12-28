package it.polito.teaching.cv;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.bytedeco.javacpp.indexer.FloatIndexer;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.imencode;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_videoio.VideoCapture;

/**
 * The controller associated with the only view of our application. The
 * application logic is implemented here. It handles the button for
 * starting/stopping the camera, the acquired video stream, the relative
 * controls and the histogram creation.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @version 1.1 (2015-10-20)
 * @since 1.0 (2013-11-20)
 */
public class VideoController {
    // the FXML button
    @FXML
    private Button button;
    // the FXML grayscale checkbox
    @FXML
    private CheckBox grayscale;
    // the FXML logo checkbox
    @FXML
    private CheckBox logoCheckBox;
    // the FXML grayscale checkbox
    @FXML
    private ImageView histogram;
    // the FXML area for showing the current frame
    @FXML
    private ImageView currentFrame;

    // a timer for acquiring the video stream
    private ScheduledExecutorService timer;
    // the OpenCV object that realizes the video capture
    private VideoCapture capture;
    // a flag to change the button behavior
    private boolean cameraActive;
    // the logo to be loaded
    private Mat logo;

    /**
     * Initialize method, automatically called by @{link FXMLLoader}
     */
    public void initialize() {
        this.capture = new VideoCapture();
        this.cameraActive = false;
    }

    /**
     * The action triggered by pushing the button on the GUI
     */
    @FXML
    protected void startCamera() {
        // set a fixed width for the frame
        this.currentFrame.setFitWidth(600);
        // preserve image ratio
        this.currentFrame.setPreserveRatio(true);

        if (!this.cameraActive) {
            // start the video capture
            this.capture.open(0);

            // is the video stream available?
            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // grab a frame every 33 ms (30 frames/sec)
                Runnable frameGrabber = new Runnable() {

                    @Override
                    public void run() {
                        Image imageToShow = grabFrame();
                        currentFrame.setImage(imageToShow);
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                // update the button content
                this.button.setText("Stop Camera");
            } else {
                // log the error
                System.err.println("Impossible to open the camera connection...");
            }
        } else {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.button.setText("Start Camera");

            // stop the timer
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // log the exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }

            // release the camera
            this.capture.release();
            // clean the frame
            this.currentFrame.setImage(null);
        }
    }

    /**
     * The action triggered by selecting/deselecting the logo checkbox
     */
    @FXML
    protected void loadLogo() {
        if (logoCheckBox.isSelected()) {
            // read the logo only when the checkbox has been selected
            this.logo = imread(getClass().getResource("/Poli.png").getFile());
        }
    }

    /**
     * Get a frame from the opened video stream (if any)
     *
     * @return the {@link Image} to show
     */
    private Image grabFrame() {
        // init everything
        Image imageToShow = null;
        Mat frame = new Mat();

        // check if the capture is open
        if (this.capture.isOpened()) {
            try {
                // read the current frame
                this.capture.read(frame);

                // if the frame is not empty, process it
                if (!frame.empty()) {
                    // add a logo...
                    if (logoCheckBox.isSelected() && this.logo != null) {
                        Rect roi = new Rect(frame.cols() - logo.cols(), frame.rows() - logo.rows(), logo.cols(),
                                logo.rows());
                        Mat imageROI = frame.apply(roi);//Mat imageROI = frame.submat(roi);
                        // add the logo: method #1
//						addWeighted(imageROI, 1.0, logo, 0.8, 0.0, imageROI);

                        // add the logo: method #2
                        logo.copyTo(imageROI, logo);
                    }

                    // if the grayscale checkbox is selected, convert the image
                    // (frame + logo) accordingly
                    if (grayscale.isSelected()) {
                        cvtColor(frame, frame, COLOR_BGR2GRAY);
                    }

                    // show the histogram
                    this.showHistogram(frame, grayscale.isSelected());

                    // convert the Mat object (OpenCV) to Image (JavaFX)
                    imageToShow = mat2Image(frame);
                }

            } catch (Exception e) {
                // log the error
                System.err.println("Exception during the frame elaboration: " + e);
            }
        }

        return imageToShow;
    }

    /**
     * Compute and show the histogram for the given {@link Mat} image
     *
     * @param frame the {@link Mat} image for which compute the histogram
     * @param gray  is a grayscale image?
     */
    private void showHistogram(Mat frame, boolean gray) {
        // split the frames in multiple images
        MatVector images = new MatVector(3);
        split(frame, images);

        // set the number of bins at 256
        int[] histSize = new int[]{256};
        // only one channel
        int[] channels = new int[]{0};
        // set the ranges
        float[] histRange = new float[]{0, 256};

        // compute the histograms for the B, G and R components
        Mat hist_b = new Mat();
        Mat hist_g = new Mat();
        Mat hist_r = new Mat();


        // B component or gray image
        calcHist(new MatVector(new Mat[]{images.get(0)}), channels, new Mat(), hist_b, histSize, histRange, false);
        FloatIndexer hist_b_idx = hist_b.createIndexer();

        // G and R components (if the image is not in gray scale)
        FloatIndexer hist_g_idx = null, hist_r_idx = null;
        if (!gray) {
            calcHist(new MatVector(new Mat[]{images.get(1)}), channels, new Mat(), hist_g, histSize, histRange, false);
            calcHist(new MatVector(new Mat[]{images.get(2)}), channels, new Mat(), hist_r, histSize, histRange, false);
            hist_g_idx = hist_g.createIndexer();
            hist_r_idx = hist_r.createIndexer();
        }

        // draw the histogram
        int hist_w = 150; // width of the histogram image
        int hist_h = 150; // height of the histogram image
        int bin_w = (int) Math.round(hist_w / (histSize[0] * 1d));

        Mat histImage = new Mat(hist_h, hist_w, CV_8UC3, new Scalar(0, 0, 0, 0));
        // normalize the result to [0, histImage.rows()]
        normalize(hist_b, hist_b, 0, histImage.rows(), NORM_MINMAX, -1, new Mat());

        // for G and R components
        if (!gray) {
            normalize(hist_g, hist_g, 0, histImage.rows(), NORM_MINMAX, -1, new Mat());
            normalize(hist_r, hist_r, 0, histImage.rows(), NORM_MINMAX, -1, new Mat());
        }

        // effectively draw the histogram(s)
        for (int i = 1; i < histSize[0]; i++) {
            // B component or gray image
            line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_b_idx.get(i - 1, 0, 0))),
                    new Point(bin_w * (i), hist_h - Math.round(hist_b_idx.get(i, 0, 0))), Scalar.all(0), 2, 8, 0);
            // G and R components (if the image is not in gray scale)
            if (!gray) {
                line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_g_idx.get(i - 1, 0, 0))),
                        new Point(bin_w * (i), hist_h - Math.round(hist_g_idx.get(i, 0, 0))), Scalar.all(0), 2, 8, 0);
                line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_r_idx.get(i - 1, 0, 0))),
                        new Point(bin_w * (i), hist_h - Math.round(hist_r_idx.get(i, 0, 0))), Scalar.all(0), 2, 8, 0);
            }
        }

        // display the histogram...
        Image histImg = mat2Image(histImage);
        this.histogram.setImage(histImg);

    }

    /**
     * Convert a Mat object (OpenCV) in the corresponding Image for JavaFX
     *
     * @param frame the {@link Mat} representing the current frame
     * @return the {@link Image} to show
     */
    private Image mat2Image(Mat frame) {
        // create a temporary buffer
        byte[] buffer = new byte[frame.cols() * frame.rows() * frame.channels()];
        // encode the frame in the buffer, according to the PNG format
        imencode(".png", frame, buffer);

        // build and return an Image created from the image encoded in the
        // buffer
        return new Image(new ByteArrayInputStream(buffer));
    }


}
