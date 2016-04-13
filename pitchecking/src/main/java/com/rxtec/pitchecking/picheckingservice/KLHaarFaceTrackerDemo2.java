package com.rxtec.pitchecking.picheckingservice;


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.openimaj.image.DisplayUtilities;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.MBFImage;
import org.openimaj.image.colour.RGBColour;
import org.openimaj.image.processing.face.detection.DetectedFace;
import org.openimaj.image.processing.face.tracking.KLTHaarFaceTracker;
import org.openimaj.video.Video;
import org.openimaj.video.VideoDisplay;
import org.openimaj.video.VideoDisplayListener;
import org.openimaj.video.capture.VideoCapture;

import com.rxtec.pitchecking.gui.FaceCheckFrame;

public class KLHaarFaceTrackerDemo2 {

	private KLTHaarFaceTracker faceTracker = new KLTHaarFaceTracker( 40 );
	private final static FaceCheckFrame faceCheckFrame = new FaceCheckFrame();
	
	private static void createUI() {
		// create the window
		
		((JFrame)faceCheckFrame).setVisible(true);
	}
	

	

	public static void main(String[] args) {
		try {
			createUI();
			FaceTrackingService.getInstance().setVideoPanel(faceCheckFrame.getVideoPanel());
			FaceTrackingService.getInstance().beginVideoCaptureAndTracking();
			
			FaceTrackingService.getInstance().beginCheckingFace(createIDCard());
			FaceCheckingService.getInstance().beginFaceTrackThread();
			
			
			while(true){
				FaceData fd = FaceCheckingService.getInstance().getCheckedFaceData();
				faceCheckFrame.setResultValue(fd.getFaceCheckResult());
			}
			
		} catch (final Exception e) {
			// an error occured
			JOptionPane.showMessageDialog(null, "Unable to open video.");
		}
	}
	
	private static IDCard createIDCard(){
		IDCard card = new IDCard();
		BufferedImage bi = null;
		try {
			bi = ImageIO.read(new File("C:/DCZ/20160412/llp.jpg"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		card.setCardImage(bi);
		return card;
	}
}
