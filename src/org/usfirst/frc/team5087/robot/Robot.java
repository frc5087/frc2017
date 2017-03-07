
package org.usfirst.frc.team5087.robot;

import com.ctre.CANTalon;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.AnalogGyro;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.Timer;
//import edu.wpi.first.wpilibj.RobotDrive;
//import edu.wpi.first.wpilibj.CameraServer;

/*
 * 
 */

public class Robot extends SampleRobot
{
	// Buttons from the Logitech controller.
	
    static final int	BUTTON_A		= 1;
    static final int	BUTTON_B		= 2;
    static final int	BUTTON_X		= 3;
    static final int	BUTTON_Y		= 4;
    static final int	BUTTON_LT		= 5;
    static final int	BUTTON_RT		= 6;
    static final int	BUTTON_BACK	= 7;
    static final int	BUTTON_START	= 8;
    
    // Commands to send to the camera control thread.
    
    static final int FRONT_CAMERA	= 0;
    static final int REAR_CAMERA	= 1;

    static final int CLIMB_START	= 0;
    static final int CLIMB_ATTACH	= 1;
    static final int CLIMB_CLIMBING	= 2;
    static final int CLIMB_STOPPED	= 3;

    static final Scalar BLACK   = new Scalar(  0,   0,   0);
    static final Scalar RED     = new Scalar(  0,   0, 255);
    static final Scalar GREEN   = new Scalar(  0, 255,   0);
    static final Scalar BLUE    = new Scalar(255,   0,   0);
    static final Scalar MAGENTA = new Scalar(255,   0, 255);
    static final Scalar WHITE   = new Scalar(255, 255, 255);

    // List of installed hardware on the robot.
    
    final	boolean	installedDrive_			= false;
    final	boolean	installedGyro_			= false;
    final	boolean	installedJoystick_		= false;
    final	boolean	installedFrontCamera_	= true;
    final	boolean	installedRearCamera_	= false;
    final	boolean	installedClimb_			= false;
    final	boolean	installedGearDrop_		= false;
    
    double angleSetpoint = 0.0;
    
    final double pGain = .006; //proportional turning constant

    //gyro calibration constant, may need to be adjusted;
    //gyro value of 360 is set to correspond to one full revolution
    
    final double voltsPerDegreePerSecond = .0128;
    
    Stack<Integer>		cameraControl_;
    
    int					cameraInUse_;
    
    UsbCamera			usbFrontCamera_;
    UsbCamera			usbRearCamera_;
    
    CvSink				cvFrontSink_;
    CvSink				cvRearSink_;
    
    CvSource			outputStream_;
    
    double				speedLimit_;

    CANTalon			leftFront_;
    CANTalon			leftRear_;
    CANTalon			rightFront_;
    CANTalon			rightRear_;
    
    CANTalon			climbLeft_;
    CANTalon			climbRight_;

    int					climbState_;

    double				climbRate_;
    double				climbSpeed_;
    double				climbMaxSpeed_;
    
    Movement			movement_;

    RamsRobotDrive		drive_;
    AnalogGyro			gyro_;
    Joystick			joystick_;
    Solenoid			drop_;

	boolean 			dropGearButton_;
	boolean			cameraSwitchButton_;
	
    /*
     * Main robot constructor - this is called before RobotInit().
     */
    
    @SuppressWarnings("unused")
	public Robot()
    {
    	System.out.println("-> Robot()");
    	
    	cameraControl_ = new Stack<Integer>();
    	
    	cameraSwitchButton_ = false;
    	
    	// Motor controllers for the robot movement.

    	if(installedDrive_ == true)
    	{
        	leftFront_  = new CANTalon(4);
        	leftRear_   = new CANTalon(8);
        	rightFront_ = new CANTalon(1);
        	rightRear_  = new CANTalon(2);
    	
        	// Not sure if these need setting, but lets do it anyway.
        	
        	leftFront_.configMaxOutputVoltage(12.0);
        	leftFront_.configNominalOutputVoltage(12.0, 12.0);
        	leftFront_.configPeakOutputVoltage(12.0,  12.0);

        	leftRear_.configMaxOutputVoltage(12.0);
        	leftRear_.configNominalOutputVoltage(12.0, 12.0);
        	leftRear_.configPeakOutputVoltage(12.0,  12.0);
        	
        	rightFront_.configMaxOutputVoltage(12.0);
        	rightFront_.configNominalOutputVoltage(12.0, 12.0);
        	rightFront_.configPeakOutputVoltage(12.0,  12.0);

        	rightRear_.configMaxOutputVoltage(12.0);
        	rightRear_.configNominalOutputVoltage(12.0, 12.0);
        	rightRear_.configPeakOutputVoltage(12.0,  12.0);

        	movement_ = new Movement(leftFront_, rightRear_);
        
        	drive_ = new RamsRobotDrive(leftRear_ ,leftFront_, rightRear_, rightFront_);
            
            drive_.setExpiration(0.1f);
            
            speedLimit_ = 0.50;			// Max of 50% speed for movement.
    	}
    	
    	if(installedClimb_ == true)
    	{
        	climbLeft_  = new CANTalon(16);
        	climbRight_ = new CANTalon(17);

        	climbLeft_.configMaxOutputVoltage(12.0);
        	climbLeft_.configNominalOutputVoltage(12.0, 12.0);
        	climbLeft_.configPeakOutputVoltage(12.0,  12.0);

        	climbRight_.configMaxOutputVoltage(12.0);
        	climbRight_.configNominalOutputVoltage(12.0, 12.0);
        	climbRight_.configPeakOutputVoltage(12.0,  12.0);

        	climbSpeed_		= 0.0;
    		climbRate_		= 0.0;
    		climbMaxSpeed_	= 0.0;
    		
        	climbState_ = CLIMB_START;
    	}

        // Variables for the gyro.

    	if(installedGyro_ == true)
    	{
            gyro_ = new AnalogGyro(0);
            
            gyro_.initGyro();
            gyro_.reset();
    	}
    	
        // Allocate a new joystick for the robot control.

    	if(installedJoystick_ == true)
    	{
    		joystick_ = new Joystick(0);
    	}

        // Variables required to handle dropping the gear.

        if(installedGearDrop_ == true)
        {
            drop_ = new Solenoid(0);
            
            drop_.set(false);
            
            dropGearButton_ = false;
        }
        
    	System.out.println("<- Robot()");
    }
    
    // This code is run when the robot is first started up. It's called after the constructor,
    // but at this point we are guaranteed that the WPIlib is ready for use. 

    /*
     * (non-Javadoc)
     * @see edu.wpi.first.wpilibj.SampleRobot#robotInit()
     */
    
    @SuppressWarnings("unused")
	public void robotInit()
    {
    	System.out.println("-> robotInit()");
        
        new Thread(() ->
        {
        	System.out.println("-> Thread()");

        	cameraInUse_ = FRONT_CAMERA;

        	// Front camera is used for dropping the gear off and will be used by the OpenCV code.
    
        	if(installedFrontCamera_ == true)
        	{
            	usbFrontCamera_ = CameraServer.getInstance().startAutomaticCapture(0);

                usbFrontCamera_.setResolution(320, 240);
                usbFrontCamera_.setFPS(15);

                cvFrontSink_ = CameraServer.getInstance().getVideo(usbFrontCamera_);
            	
        		cvFrontSink_.setEnabled(true);
            	cvFrontSink_.setSource(usbFrontCamera_);
            	
                /*
                
                // Grab the JSON file and save into a table for this.

                VideoProperty camera0[] = usbFrontCamera_.enumerateProperties();

                for(int i = camera0.length(); ++i)
                {
                	
                }
                */
        	}
            
            // The rear camera is only used when we are looking for the rope.

        	if(installedRearCamera_ == true)
        	{
            	usbRearCamera_ = CameraServer.getInstance().startAutomaticCapture(1);

                usbRearCamera_.setResolution(320, 240);
                usbRearCamera_.setFPS(15);

            	cvRearSink_ = CameraServer.getInstance().getVideo(usbRearCamera_);

        		cvRearSink_.setEnabled(false);
            	cvRearSink_.setSource(usbRearCamera_);
        	}
        	
        	outputStream_ = CameraServer.getInstance().putVideo("Robot Camera", 320, 240);

        	// Create the Mat data required by the capture code.
        	
			Mat		original = new Mat();

			Mat		hsv		 = new Mat();
			Mat 	image 	 = new Mat();

			Mat 	temp 	 = new Mat();

			List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

			// Create the low and high HSV values to generate the contours.
			
			Scalar	lowHSV  = new Scalar(55, 100, 100);
			Scalar	highHSV = new Scalar(95, 255, 255);

			// Points for the cross-hair lines.
			
    		Point crossH0 = new Point(160 - 8, 120);
    		Point crossH1 = new Point(160 + 8, 120);

    		Point crossV0 = new Point(160, 120 - 8);
    		Point crossV1 = new Point(160, 120 + 8);
    		
        	System.out.println("Running ...");
        	
            while(!Thread.interrupted())
            {
            	// Switch cameras if requested by the main application.
            	
            	if(cameraControl_.empty() == false)
            	{
            		int control = (int) cameraControl_.pop();
            		
            		switch(control)
            		{
        				case FRONT_CAMERA :
        				{
        					if(installedFrontCamera_ == true)
        					{
        						cameraInUse_ = FRONT_CAMERA;

        						cvFrontSink_.setEnabled(true);
        						cvRearSink_.setEnabled(false);
        					}
        					else
        					{
        						System.out.println("Front camera not installed.");
        					}

                			break;
        				}
        			
            			case REAR_CAMERA :
            			{
            				if(installedRearCamera_ == true)
            				{
            					cameraInUse_ = REAR_CAMERA;

                        		cvFrontSink_.setEnabled(false);
                        		cvRearSink_.setEnabled(true);
            				}
        					else
        					{
        						System.out.println("Rear camera not installed.");
        					}

                    		break;
            			}
            			
            			default :
            			{
                			System.out.println(control + " - invalid control code.");
                			
            				break;
            			}
            		}
            	}
            	
            	switch(cameraInUse_)
            	{
            		case FRONT_CAMERA :
            		{
            			if(installedFrontCamera_ == true)
            			{
                    		cvFrontSink_.grabFrameNoTimeout(original);

                    		// Convert the grabbed image to HSV format so we can work with it.

                    		Imgproc.cvtColor(original, hsv, Imgproc.COLOR_BGR2HSV);

                    		// Grab an black and white image with white as the selected area.

                    		Core.inRange(hsv, lowHSV, highHSV, image);

                			hsv.release();

                    		// Clear the previous contours and grab the new ones.
                    		
                    		contours.clear();
                    		
                    		Imgproc.findContours(image, contours, temp,
                    							 Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                			image.release();
                            temp.release();

                    		// Grab the co-ords of the corners of the box(es) from the contour list.

                            // Send the information to the main code.
                            
                    		// Show the found contours for the user.
                    		
                    		for(int i = 0; i < contours.size(); ++i)
            				{
                    			Imgproc.drawContours(original, contours, i, WHITE);
            				}

                    		// Display the cross-hair in the centre of the screen.
                    		
                    		Imgproc.line(original, crossH0, crossH1, MAGENTA);
                    		Imgproc.line(original, crossV0, crossV1, MAGENTA);

                            outputStream_.putFrame(original);

                    		original.release();
            			}
            			
                		break;
            		}
            		
            		case REAR_CAMERA :
            		{
            			if(installedRearCamera_ == true)
            			{
                    		cvRearSink_.grabFrameNoTimeout(original);

                            outputStream_.putFrame(original);

                    		original.release();
            			}

                		break;
            		}
            		
            		default :
            		{
            			System.out.println(cameraInUse_ + " - invalid camera id.");
            			
            			break;
            		}
            	}
            }

            System.out.println("Stopped.");
            
        	System.out.println("<- Thread()");

        }).start();
        
    	System.out.println("<- robotInit()");
    }

    /*
     * (non-Javadoc)
     * @see edu.wpi.first.wpilibj.SampleRobot#disabled()
     */
    
    public void disabled()
    {
    	System.out.println("-> disabled()");
    
    	System.out.println("<- disabled()");
    }

    /*
     * (non-Javadoc)
     * @see edu.wpi.first.wpilibj.SampleRobot#autonomous()
     */
    
    public void autonomous()
    {
    	System.out.println("-> autonomous()");
        
        while(isEnabled())
        {
        }
        
    	System.out.println("<- autonomous()");
    }

    /*
     * (non-Javadoc)
     * @see edu.wpi.first.wpilibj.SampleRobot#operatorControl()
     */
    
    public void operatorControl()
    {
    	System.out.println("-> operatorControl()");
    	
        drive_.setSafetyEnabled(true);
        
        while(isOperatorControl() && isEnabled())
        {
        	moveRobot();
        	
        	dropGear();
        	
        	switchCamera();
        	
        	climbRope();
        	
            Timer.delay(0.005);
        }
        
    	System.out.println("<- operatorControl()");
    }

    /*
     * (non-Javadoc)
     * @see edu.wpi.first.wpilibj.SampleRobot#test()
     */
    
    public void test()
    {
    	System.out.println("-> test()");
    	
    	int leftRight = 1;

    	double leftMotor = 1.0;
    	double rightMotor = 1.0;
    	
    	double slowDown = 0.001;
    	
        int steps = 0;

        drive_.setSafetyEnabled(true);

    	while(isEnabled())
    	{
        	drive_.setLeftRightMotorOutputs(leftMotor, rightMotor);

        	if((++steps % 25) == 0)
        	{
        		double stamp = Timer.getFPGATimestamp();
        		
        		double left  = movement_.movement(0);
        		double right = movement_.movement(1);
        		
           		System.out.format("%2.6f:%2.6f %2.6f %2.6f %2.6f\n",
           			stamp,
           			leftMotor, rightMotor,
           			left, right);

           		if(Math.abs(left - right) == 0.0)
           		{
           			continue;
           		}
           				
           		if(Math.abs(left - right) < (2.0 / 4096.0))
           		{
           			break;
           		}
           		
           		if(leftRight == -1)
           		{
               		if(left > right)
               		{
               			leftRight = 0;
               		}
               		else
               		{
               			leftRight = 1;
               		}
           		}
           		else
           		{
               		if(leftRight == 0)
               		{
               			// The left distance was greater than the right, so we need to slow the
               			// left motor(s) down to match the right.

               			leftMotor -= slowDown;
               		}
               		else
               		{
               			// The right distance was greater than the left, so we need to slow the
               			// right motor(s) down to match the left.

               			rightMotor -= slowDown;
               		}
           		}
        	}

        	Timer.delay(0.005);
    	}

    	drive_.setLeftRightMotorOutputs(0.0, 0.0);

    	System.out.println("<- test()");
    }

    /*
     * Handle the movement of the robot and adjust depending on the camera being used
     * at the time. 
     */
    
    @SuppressWarnings("unused")
	private void moveRobot()
    {
//      double turningValue;
        
//      gyro.setSensitivity(voltsPerDegreePerSecond); //calibrates gyro values to equal degrees

//    	turningValue =  (angleSetpoint - gyro.getAngle()) * pGain;

    	if(installedDrive_ == true)
    	{
        	switch(cameraInUse_)
        	{
        		case FRONT_CAMERA :
        		{
                	drive_.arcadeDrive(-joystick_.getY() * speedLimit_,
                					   -joystick_.getX() * speedLimit_, true);
                	
        			break;
        		}
        		
        		case REAR_CAMERA :
        		{
                	drive_.arcadeDrive(+joystick_.getY() * speedLimit_,
    								   -joystick_.getX() * speedLimit_, true);
                	
        			break;
        		}
        		
        		default :
        		{
        			break;
        		}
        	}
    	}
    }
    
	/*
	 * If the <A> button is pressed and held, open the gear/cog holder, otherwise
	 * close the gear/cog holder.
	 */
    
    @SuppressWarnings("unused")
	private void dropGear()
    {
    	if(installedJoystick_ == true)
    	{
            if(joystick_.getRawButton(BUTTON_A) == true)
            {
            	dropGearButton_ = true;

            	if(installedGearDrop_ == true)
            	{
            		drop_.set(true);
            	}
            }
            else
            {
        		if(dropGearButton_ == true)
            	{
            		dropGearButton_ = false;
            		
                	if(installedGearDrop_ == true)
                	{
                		drop_.set(false);
                	}
            	}
            }
    	}
    }

    /*
     * The <START> button on the joystick switches the camera we are viewing which also
     * switches the controls on the robot.
     */
    
    @SuppressWarnings("unused")
	private void switchCamera()
    {
    	if(installedJoystick_ == true)
    	{
            if(joystick_.getRawButton(BUTTON_START) == true)
            {
            	if(cameraSwitchButton_ == false)
            	{
            		if(cameraInUse_ == FRONT_CAMERA)
            		{
            			if(installedRearCamera_ == true)
            			{
            				cameraControl_.push((Integer) REAR_CAMERA);
            			}
            		}
            		else
            		{
            			if(installedFrontCamera_ == true)
            			{
            				cameraControl_.push((Integer) FRONT_CAMERA);
            			}
            		}
            		
            		cameraSwitchButton_ = true;
            	}
            }
            else
            {
            	if(cameraSwitchButton_ == true)
            	{
            		cameraSwitchButton_ = false;
            	}
            }
    	}
    }

    /*
     * Handle the climbing of the robot, only if we are viewing via the rear camera.
     */

    private int counter_ = 0;
    
    @SuppressWarnings("unused")
	private void climbRope()
    {
    	if((installedClimb_ == true)
    	&& (cameraInUse_ == REAR_CAMERA))
    	{
    		if((++counter_ % 10) == 0)
    		{
    			System.out.format("%1.4f[%1.4f]:%2.4f/%2.4f\n",
    				climbSpeed_,
    				climbRate_,
    				climbLeft_.getOutputCurrent(),
    				climbRight_.getOutputCurrent());
    		}
    		
    		// Set the motor climb speed.
    		
			climbLeft_.set(-climbSpeed_);
			climbRight_.set(-climbSpeed_);

			// Adjust the climbing speed.
			
    		climbSpeed_ += climbRate_;
			
			if(climbSpeed_ > climbMaxSpeed_)
			{
				climbSpeed_ = climbMaxSpeed_;
				
				climbRate_  = 0.0;
			}

			// Grab the max current draw.
			
    		double current = Math.max(climbLeft_.getOutputCurrent(),
					   				   climbRight_.getOutputCurrent());


    		switch(climbState_)
    		{
    			case CLIMB_START :
    			{
    				climbSpeed_	   = 0.1;
    				
    				climbRate_ 	   = 0.005;
    				climbMaxSpeed_ = 0.25;
    				
    				climbState_	   = CLIMB_ATTACH;
    				
    				break;
    			}
    			
    			case CLIMB_ATTACH :
    			{
    				if(current >= 3.0)
    				{
    					climbRate_	   = 0.001;
    					climbMaxSpeed_ = 0.6;
    					
    					climbState_	   = CLIMB_CLIMBING;
    				}
    				
        			break;
    			}
    			
    			case CLIMB_CLIMBING :
    			{
            		if(current >= 15.0)
            		{
            			climbState_ = CLIMB_STOPPED;
            			
            			climbSpeed_    = 0.0;
            			climbRate_	   = 0.0;
            			climbMaxSpeed_ = 0.0;
            		}
            		
        			break;
    			}
    			
    			case CLIMB_STOPPED :
    			{
    				// Allow small movements to ensure the pad light is lit.
    				
        			break;
    			}
    		}
    	}
    }
}