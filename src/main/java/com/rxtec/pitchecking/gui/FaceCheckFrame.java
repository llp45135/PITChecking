package com.rxtec.pitchecking.gui;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Font;
import javax.swing.JTabbedPane;
import javax.swing.JLayeredPane;
import javax.swing.JTextArea;
import javax.swing.JList;
import javax.swing.JSeparator;
import java.awt.Canvas;
import java.awt.Color;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.border.LineBorder;
import java.awt.Rectangle;

public class FaceCheckFrame extends JFrame {

	private JPanel contentPane;
	private JButton showBmp;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					FaceCheckFrame frame = new FaceCheckFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private JPanel videoPanel = new JPanel();

	
	
	
	public JPanel getVideoPanel() {
		return videoPanel;
	}

	/**
	 * Create the frame.
	 */
	public FaceCheckFrame() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 640, 480);
		contentPane = new JPanel();
//		contentPane.setToolTipText("ddd");
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
//		contentPane.add(showBmp);

		JLabel lblNewLabel = new JLabel("\u8BF7\u5E73\u89C6\u6444\u50CF\u5934");
		lblNewLabel.setFont(new Font("微软雅黑", Font.PLAIN, 28));
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setBounds(204, 30, 222, 38);
		contentPane.add(lblNewLabel);
		
		JPanel panel = new JPanel();
		panel.setName("");
		panel.setBorder(new LineBorder(new Color(0, 0, 0), 1, true));
		panel.setToolTipText("");
		panel.setBounds(496, 71, 118, 152);
		panel.setLayout(null);
		contentPane.add(panel);
		
		showBmp = new JButton("");
		showBmp.setBounds(10, 10, 100, 130);
		panel.add(showBmp);
		
		videoPanel.setBorder(new LineBorder(new Color(0, 0, 0)));
		videoPanel.setBounds(20, 71, 453, 324);
		contentPane.add(videoPanel);
		
		
		resultLabel.setFont(new Font("宋体", Font.PLAIN, 20));
		resultLabel.setBounds(496, 290, 118, 56);
		contentPane.add(resultLabel);

		
	}

	public void setIdcardBmp(ImageIcon icon) {
		this.showBmp.setIcon(icon);
	}
	private static void addPopup(Component component, final JPopupMenu popup) {
		component.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}
			private void showMenu(MouseEvent e) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}
	JLabel resultLabel = new JLabel("New label");
	
	public void updateFaceCheckResult(String s){
		
		resultLabel.setText(s);
		
	}
}