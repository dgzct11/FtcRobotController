package org.firstinspires.ftc.teamcode.robots.reach;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.teamcode.robots.reach.utils.CanvasUtils;
import org.firstinspires.ftc.teamcode.robots.reach.utils.Constants;
import org.firstinspires.ftc.teamcode.robots.reach.utils.TrikeChassisMovementCalc;
import org.firstinspires.ftc.teamcode.robots.reach.vision.StackHeight;
import org.firstinspires.ftc.teamcode.robots.reach.utils.CanvasUtils.Point;
import org.firstinspires.ftc.teamcode.util.PIDController;
import org.firstinspires.ftc.teamcode.vision.SkystoneGripPipeline;
import org.firstinspires.ftc.teamcode.vision.TowerHeightPipeline;
import org.firstinspires.ftc.teamcode.vision.Viewpoint;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Arrays;

import static org.firstinspires.ftc.teamcode.util.utilMethods.diffAngle2;
import static org.firstinspires.ftc.teamcode.util.utilMethods.futureTime;
import static org.firstinspires.ftc.teamcode.util.utilMethods.wrap360;
import static org.firstinspires.ftc.teamcode.util.utilMethods.wrapAngle;
import static org.firstinspires.ftc.teamcode.util.utilMethods.wrapAngleMinus;

/**
 * The Pose class stores the current real world position/orientation:
 * <b>position</b>, <b>heading</b>, and <b>speed</b> of the robot.
 *
 * This class should be a point of reference for any navigation classes that
 * want to know current orientation and location of the robot. The update method
 * must be called regularly, it monitors and integrates data from the
 * orientation (IMU) and odometry (motor encoder) sensors.
 * 
 * @author plethora of ironreign programmers
 * @version 17564.70_b
 * @since 2018-11-02
 */

@Config
public class ReachPose {

    // setup
    HardwareMap hwMap;
    Telemetry telemetry;
    PIDController turnPID = new PIDController(0, 0, 0);
    PIDController distPID = new PIDController(0, 0, 0);
    PIDController swervePID = new PIDController(0,0,0);
    private int autoAlignStage = 0;
    FtcDashboard dashboard;
    public static double driveP = .01;
    public static double turnP = 0.0055; // proportional constant applied to error in degrees /.0055 seems very low
    public static double turnI = 0.0; // integral constant
    public static double turnD = .13; // derivative constant

    public static double distP = 0.5; // proportional constant applied to error in meters
    public static double distI = 0.0; // integral constant
    public static double distD = .19; // derivative constant

    public static double swerveP = .1;
    public static double swerveI = 0;
    public static double swerveD = 0;

    public static double cutout = 1.0;



    public double headingP = 0.007;
    public double headingD = 0;

    public double balanceP = .35;
    public double balanceD = 3.1444;

    public long[] visionTimes = new long[] {};

    // All Actuators
    private DcMotorEx motorFrontRight = null;
    private DcMotorEx motorFrontLeft = null;
    private DcMotorEx motorMiddle = null;
    private DcMotorEx motorMiddleSwivel = null;
    private DcMotor duckSpinner = null;
    RevBlinkinLedDriver blinkin = null;

    // All sensors
    BNO055IMU imu; // Inertial Measurement Unit: Accelerometer and Gyroscope combination sensor
    private DistanceSensor chassisLengthSensor;

    // DigitalChannel magSensor;

    // drive train power values
    private double powerLeft = 0;
    private double powerRight = 0;

    // mecanum types
    private double powerFrontLeft = 0;
    private double powerFrontRight = 0;
    private double powerMiddle = 0;
    private double powerMiddleSwivel = 0;

    // PID values
    public int forwardTPM = 2109;// todo- use drive IMU to get this perfect
    int rightTPM = 2109; // todo - these need to be tuned for each robot
    int leftTPM = 2109; // todo - these need to be tuned for each robot
    private int strafeTPM = 1909; // todo - fix value high priority this current value is based on Kraken -

    // minimech will be different
    public static double poseX;
    public static double poseY;
    private static double poseHeading; // current heading in degrees. Might be rotated by 90 degrees from imu's heading when strafing
    private double poseHeadingRad; // current heading converted to radians
    private double poseSpeed;
    private double velocityX, velocityY;
    private double posePitch;
    private double poseRoll;
    private long timeStamp; // timestamp of last update
    private boolean initialized = false; //todo: this should never be static in competition
    public double offsetHeading; //todo- maybe static
    private double offsetPitch;
    private double offsetRoll;
    private double goalHeading;

    public static double displacement;
    private double displacementPrev;
    private double odometer;

    private double cachedXAcceleration;
    private double lastXAcceleration;

    private double lastUpdateTimestamp = 0;
    private double loopTime = 0;

    private long turnTimer = 0;
    private boolean turnTimerInit = false;
    private double minTurnError = 2.0;
    public boolean maintainHeadingInit = false;

    private double poseSavedHeading = 0.0;

    public int servoTesterPos = 1600;
    public double autonomousIMUOffset = 0;

    public boolean rangeIsHot = true;

    // vision related
    public SkystoneGripPipeline pipeline;
    public TowerHeightPipeline towerHeightPipeline;

    public enum MoveMode {
        forward, backward, left, right, rotate, still;
    }

    protected MoveMode moveMode;

    public enum Articulation { // serves as a desired robot articulation which may include related complex movements of the elbow, lift and supermanLeft
        inprogress, // currently in progress to a final articulation
        manual, // target positions are all being manually overridden
        cardinalBaseRight,
        cardinalBaseLeft,
        returnHome,
    }

    public enum RobotType {
        BigWheel, Icarus, Minimech, TomBot, UGBot, Reach;
    }

    public RobotType currentBot;

    public Articulation getArticulation() {
        return articulation;
    }

    protected Articulation articulation = Articulation.manual;
    double articulationTimer = 0;

    Orientation imuAngles; // pitch, roll and yaw from the IMU
    // roll is in x, pitch is in y, yaw is in z


    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    //// ////
    //// Constructors ////
    //// ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    public ReachPose(RobotType name) {
        poseX = Constants.startingXOffset;
        poseY = Constants.startingYOffset;
        poseHeading = 0;
        poseSpeed = 0;
        posePitch = 0;
        poseRoll = 0;
        currentBot = name;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    ////                                                                                  ////
    ////                                   Init/Update                                    ////
    ////                                                                                  ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Initializes motors, servos, lights and sensors from a given hardware map
     *
     * @param ahwMap Given hardware map
     */
    public void init(HardwareMap ahwMap, Telemetry telemetry) {
        hwMap = ahwMap;
        this.telemetry = telemetry;
        /*
         * eg: Initialize the hardware variables. Note that the strings used here as
         * parameters to 'get' must correspond to the names assigned during the robot
         * configuration step (using the FTC Robot Controller app on the phone).
         */

        // create hwmap with config values
        // this.driveLeft = this.hwMap.dcMotor.get("driveLeft");
        // this.driveRight = this.hwMap.dcMotor.get("driveRight");

        this.blinkin = hwMap.get(RevBlinkinLedDriver.class, "blinkin");
        motorFrontLeft = hwMap.get(DcMotorEx.class, "motorFrontLeft");
        motorFrontRight = hwMap.get(DcMotorEx.class, "motorFrontRight");
        motorMiddle= hwMap.get(DcMotorEx.class, "motorMiddle");
        motorMiddleSwivel = hwMap.get(DcMotorEx.class, "motorMiddleSwivel");
        duckSpinner = hwMap.get(DcMotor.class, "duckSpinner");

        // elbow.setDirection(DcMotor.Direction.REVERSE);

        //reverse chassis motors on one side only - because they are reflected
        motorFrontLeft.setDirection(DcMotor.Direction.REVERSE);
        motorFrontRight.setDirection(DcMotor.Direction.FORWARD);
        duckSpinner.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);


       //set coasting/braking preference
         motorFrontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
         motorFrontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
         motorMiddle.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
         motorMiddleSwivel.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        // turretMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        // magSensor.setMode(DigitalChannel.Mode.INPUT);

        //flywheelMotor.setDirection(DcMotor.Direction.REVERSE);

        // behaviors of motors
        /*
         * driveLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
         * driveRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE); if
         * (this.currentBot == RobotType.BigWheel) {
         * driveLeft.setDirection(DcMotorSimple.Direction.FORWARD);
         * driveRight.setDirection(DcMotorSimple.Direction.REVERSE); } else {
         * driveLeft.setDirection(DcMotorSimple.Direction.REVERSE);
         * driveRight.setDirection(DcMotorSimple.Direction.FORWARD); }
         */
        // setup subsystems
        moveMode = MoveMode.still;

        // setup both IMU's (Assuming 2 rev hubs
        BNO055IMU.Parameters parametersIMU = new BNO055IMU.Parameters();
        parametersIMU.angleUnit = BNO055IMU.AngleUnit.DEGREES;
        parametersIMU.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
        parametersIMU.loggingEnabled = true;
        parametersIMU.loggingTag = "baseIMU";

        imu = hwMap.get(BNO055IMU.class, "baseIMU");
        imu.initialize(parametersIMU);

        Constants.ALLIANCE_INT_MOD = Math.abs(Constants.ALLIANCE_INT_MOD);

        // sensors
        chassisLengthSensor = this.hwMap.get(DistanceSensor.class, "distLength");

        // initialize vision

//        VuforiaLocalizer vuforia;
//        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();
//        parameters.vuforiaLicenseKey = RC.VUFORIA_LICENSE_KEY;
//        parameters.cameraName = hwMap.get(WebcamName.class, "Webcam 1");
//        vuforia = ClassFactory.getInstance().createVuforia(parameters);
//        Vuforia.setFrameFormat(PIXEL_FORMAT.RGB565, true);
//        vuforia.setFrameQueueCapacity(1);
//        towerHeightPipeline = new TowerHeightPipeline(hwMap, vuforia);

        // dashboard
        dashboard = FtcDashboard.getInstance();
    }

    public void setVisionTimes(long[] visionTimes) {
        this.visionTimes = visionTimes;
    }

    private void initVuforia(HardwareMap hardwareMap, Viewpoint viewpoint) {

    }

    public void resetIMU() {
        BNO055IMU.Parameters parametersIMU = new BNO055IMU.Parameters();
        parametersIMU.angleUnit = BNO055IMU.AngleUnit.DEGREES;
        parametersIMU.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
        parametersIMU.loggingEnabled = true;
        parametersIMU.loggingTag = "IMU";

        imu.initialize(parametersIMU);
    }

    public void resetEncoders() {
    }

    private StackHeight detection = StackHeight.HOLD_STATE;

    public void setDetection(StackHeight detection) {this.detection = detection;}
    public StackHeight getDetection() {return detection;}

    private int frameCount;
    public void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    private float visionFPS;
    public void setVisionFPS(float visionFPS) {
        this.visionFPS = visionFPS;
    }

    private int totalFrameTimeMs;
    public void setTotalFrameTimeMs(int totalFrameTimeMs) {
        this.totalFrameTimeMs = totalFrameTimeMs;
    }

    private int pipelineTimeMs;
    public void setPipelineTimeMs(int pipelineTimeMs) {
        this.pipelineTimeMs = pipelineTimeMs;
    }

    private int overheadTimeMs;
    public void setOverheadTimeMs(int overheadTimeMs) {
        this.overheadTimeMs = overheadTimeMs;
    }

    private int currentPipelineMaxFps;
    public void setCurrentPipelineMaxFps(int currentPipelineMaxFps) {
        this.currentPipelineMaxFps = currentPipelineMaxFps;
    }

    private double aspectRatio;
    public void setAspectRatio(double aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public void sendTelemetry() {
        TelemetryPacket packet = new TelemetryPacket();
        Canvas fieldOverlay = packet.fieldOverlay();

        Point posePoint = new Point(getPoseX(), getPoseY());

        telemTest.updateVals(Constants._t, Constants._q, Constants._n, Constants._e, Constants._m);
        Point leftWheelOld = new Point(telemTest.A[0], telemTest.A[1]);
        Point rightWheelOld = new Point(telemTest.B[0], telemTest.B[1]);
        Point swerveWheelOld = new Point(telemTest.C[0], telemTest.C[1]);

        Point leftWheelNew = new Point(telemTest.W[0], telemTest.W[1]);
        Point rightWheelNew = new Point(telemTest.Z[0], telemTest.Z[1]);
        Point swerveWheelNew = new Point(telemTest.T[0], telemTest.T[1]);

        fieldOverlay.setStroke("#000000");
        fieldOverlay.strokeLine(leftWheelOld.getX(), leftWheelOld.getY(), rightWheelOld.getX(), rightWheelOld.getY());
        fieldOverlay.strokeLine(rightWheelOld.getX(), rightWheelOld.getY(), swerveWheelOld.getX(), swerveWheelOld.getY());
        fieldOverlay.strokeLine(swerveWheelOld.getX(), swerveWheelOld.getY(), leftWheelOld.getX(), leftWheelOld.getY());

        fieldOverlay.setStroke("#4D934D");
        fieldOverlay.strokeLine(leftWheelNew.getX(), leftWheelNew.getY(), rightWheelNew.getX(), rightWheelNew.getY());
        fieldOverlay.strokeLine(rightWheelNew.getX(), rightWheelNew.getY(), swerveWheelNew.getX(), swerveWheelNew.getY());
        fieldOverlay.strokeLine(swerveWheelNew.getX(), swerveWheelNew.getY(), leftWheelNew.getX(), leftWheelNew.getY());

        // goal location
        // robot location
        Point robotCanvasPoint = CanvasUtils.toCanvasPoint(posePoint);
        fieldOverlay.strokeCircle(robotCanvasPoint.getX(), robotCanvasPoint.getY(), 9);

        // robot heading (black)
        CanvasUtils.drawVector(fieldOverlay, posePoint, 2 * 9, poseHeading, "#000000");

        // vision
        packet.put("Frame Count", frameCount);
        packet.put("FPS", String.format("%.2f", visionFPS));
        packet.put("Total frame time ms", totalFrameTimeMs);
        packet.put("Pipeline time ms", pipelineTimeMs);
        packet.put("Overhead time ms", overheadTimeMs);
        packet.put("Theoretical max FPS", currentPipelineMaxFps);
        packet.put("Aspect ratio", aspectRatio);

        packet.put("detection", detection);
        packet.put("vision times", Arrays.toString(visionTimes));
        long totalTime = 0;
        for(long time: visionTimes)
            totalTime += time;
        packet.put("total time", totalTime);
        packet.put("pose y",getPoseY());
        packet.put("pose x",getPoseX());
        packet.put("velocity x", velocityX);
        packet.put("velocity y", velocityY);
        packet.put("target angle for the thing", goalHeading);
        packet.put("avg ticks",getAverageTicks());
        packet.put("voltage", getBatteryVoltage());
        packet.put("zero indicator", 0);
        packet.put("base error", turnPID.getError());
        packet.put("base integrated error", turnPID.getTotalError());
        packet.put("base derivative error", turnPID.getDeltaError());
        packet.put("loop time", loopTime);
        packet.put("chassis length (m)", getChassisLength());
        dashboard.sendTelemetryPacket(packet);
    }

    double getBatteryVoltage() {
        double result = Double.POSITIVE_INFINITY;
        for (VoltageSensor sensor : hwMap.voltageSensor) {
            double voltage = sensor.getVoltage();
            if (voltage > 0) {
                result = Math.min(result, voltage);
            }
        }
        return result;
    }

    /**
     * update the current location of the robot. This implementation gets heading
     * and orientation from the Bosch BNO055 IMU and assumes a simple differential
     * steer robot with left and right motor encoders. also updates the positions of
     * robot subsystems; make sure to add each subsystem's update class as more are
     * implemented.
     * <p>
     * <p>
     * The current naive implementation assumes an unobstructed robot - it cannot
     * account for running into objects and assumes no slippage in the wheel
     * encoders. Debris on the field and the mountain ramps will cause problems for
     * this implementation. Further work could be done to compare odometry against
     * IMU integrated displacement calculations to detect stalls and slips
     * <p>
     * This method should be called regularly - about every 20 - 30 milliseconds or
     * so.
     *
     * @param imu
     * @param ticksLeft
     * @param ticksRight
     */
    double rotVelBase = 0.0;
    double prevHeading = getHeading();
    boolean flywheelIsActive = false;
    int numLoops = 0;
    int laggyCounter = 0;
    boolean autoLaunchActive = false;
    double autoLaunchTimer = 0.0;
    boolean ringChambered = false;

    public void update() {
        long currentTime = System.nanoTime();

        imuAngles = imu.getAngularOrientation().toAxesReference(AxesReference.INTRINSIC).toAxesOrder(AxesOrder.ZYX);
        if (!initialized) {
            // first time in - we assume that the robot has not started moving and that
            // orientation values are set to the current absolute orientation
            // so first set of imu readings are effectively offsets

            offsetHeading = wrapAngleMinus((double) (360 - imuAngles.firstAngle), poseHeading);
            offsetRoll = wrapAngleMinus(imuAngles.secondAngle, poseRoll);
            offsetPitch = wrapAngleMinus(imuAngles.thirdAngle, posePitch);
            initialized = true;
        }
        poseHeading = wrapAngle(360 - imuAngles.firstAngle, offsetHeading);
        posePitch = wrapAngle(imuAngles.thirdAngle, offsetPitch);
        poseRoll = wrapAngle(imuAngles.secondAngle, offsetRoll);

        articulate(articulation); // call the most recently requested articulation

        displacement = (getAverageTicks() - displacementPrev) / forwardTPM;
        odometer += Math.abs(displacement);
        poseHeadingRad = Math.toRadians(poseHeading);

        odometer += Math.abs(displacement);
        poseSpeed = displacement / ((currentTime - this.timeStamp) / 1e9); // meters per second when ticks
                                                                                      // per meter is calibrated
        velocityX = poseSpeed * Math.sin(poseHeadingRad);
        velocityY = poseSpeed * Math.cos(poseHeadingRad);

        timeStamp = currentTime;
        displacementPrev = getAverageTicks();

        rotVelBase = (getHeading() - prevHeading) / (loopTime / 1E9);
        prevHeading = getHeading();

        poseX += displacement * Math.sin(poseHeadingRad);
        poseY += displacement * Math.cos(poseHeadingRad);

        lastXAcceleration = cachedXAcceleration;
        cachedXAcceleration = imu.getLinearAcceleration().xAccel;

        loopTime = System.nanoTime() - lastUpdateTimestamp;

        if(loopTime / 1E9 > .10){
            laggyCounter++;
        }
        numLoops++;

        lastUpdateTimestamp = System.nanoTime();

        telemetry.update();
        sendTelemetry();
    }

    TrikeChassisMovementCalc telemTest = new TrikeChassisMovementCalc(0,0,0,0,0);

    //Pose version of sending telem to the phone. use this for sensors
    public void setupDriverTelemetry(boolean active, int state, Constants.Alliance ALLIANCE, double loopAvg){
        //game variables
        telemetry.addLine().addData("active", () -> active);
        telemetry.addLine().addData("state", () -> state);
        telemetry.addLine().addData("Alliance", () -> ALLIANCE);
        telemetry.addLine().addData("Loop time", "%.0fms", () -> loopAvg / 1000000);

        //robot variables
        telemetry.addLine().addData("leftVectorDist", () -> telemTest.getCalcdDistance()[0]);
        telemetry.addLine().addData("rightVectorDist", () -> telemTest.getCalcdDistance()[1]);
        telemetry.addLine().addData("leftVectorDist", () -> telemTest.getCalcdDistance()[2]);
        telemetry.addLine().addData("swerveAngle", () -> telemTest.getNewSwerveAngle());
//        telemetry.addLine().addData("chassis length (m)", () -> getChassisLength());
    }


    /**
     * Drive forwards for a set power while maintaining an IMU heading using PID
     *
     * @param Kp          proportional multiplier for PID
     * @param Ki          integral multiplier for PID
     * @param Kd          derivative proportional for PID
     * @param pwr         set the forward power
     * @param targetAngle the heading the robot will try to maintain while driving
     */
    public void driveIMU(double Kp, double Ki, double Kd, double pwr, double targetAngle) {
        movePID(Kp, Ki, Kd, pwr, poseHeading, targetAngle);
    }

    /**
     * Drive with a set power for a set distance while maintaining an IMU heading
     * using PID This is a relative version
     *
     * @param pwr          set the forward power
     * @param targetAngle  the heading the robot will try to maintain while driving
     * @param forward      is the robot driving in the forwards/left (positive)
     *                     directions or backwards/right (negative) directions
     * @param targetMeters the target distance (in meters)
     */
    boolean driveIMUDistanceInitialzed = false;
    long driveIMUDistanceTarget = 0;

    public boolean driveIMUDistance(double pwr, double targetAngle, boolean forward, double targetMeters) {

        if (!driveIMUDistanceInitialzed) {
            // set what direction the robot is supposed to be moving in for the purpose of
            // the field position calculator

            // calculate the target position of the drive motors
            driveIMUDistanceTarget = (long) Math.abs((targetMeters * forwardTPM)) + Math.abs(getAverageTicks());
            driveIMUDistanceInitialzed = true;
        }

        if (!forward) {
            moveMode = moveMode.backward;
            targetMeters = -targetMeters;
            pwr = -pwr;
        } else
            moveMode = moveMode.forward;

        // if this statement is true, then the robot has not achieved its target
        // position
        if (Math.abs(driveIMUDistanceTarget) > Math.abs(getAverageTicks())) {
            // driveIMU(Kp, kiDrive, kdDrive, pwr, targetAngle);
            driveIMU(turnP, turnI, turnD, pwr, targetAngle, false);
            return false;
        } // destination achieved
        else {
            //stopAll();
            driveMixerTrike(0, 0);
            driveIMUDistanceInitialzed = false;
            return true;
        }
    }


    public boolean driveAbsoluteDistance(double pwr, double targetAngle, boolean forward, double targetMeters, double closeEnoughDist) {
        //driveAbsoluteDistance(MaxPower, heading, forward, distance,.1)
        if (!forward) {
            moveMode = moveMode.backward;
            //targetMeters = -targetMeters;
            //pwr = -pwr;
        } else
            moveMode = moveMode.forward;

        targetAngle= wrap360(targetAngle);  //this was probably already done but repeated as a safety

        // if this statement is true, then the robot has not achieved its target
        // position
        if (Math.abs(targetMeters) > Math.abs(closeEnoughDist)) {
            //driveIMU(Kp, kiDrive, kdDrive, pwr, targetAngle);
            //driveIMU(turnP, turnI, turnD, pwr, wrap360(targetAngle), false);
            movePID(pwr, forward, targetMeters,0,getHeading(),targetAngle);
            return false;
        } // destination achieved
        else {
            stopChassis(); //todo: maybe this should be optional when you are stringing moves together
            return true;
        }
    }

    int fieldPosState = 0;
    public boolean driveToFieldPosition(double targetX, double targetY, boolean forward, double maxPower, double closeEnoughDist){

        double heading = getBearingToWrapped(targetX, targetY);

        if(!forward){ //need to reverse heading when driving backwards
            heading = wrapAngle(heading,180);
        }

        switch (fieldPosState){
            case 0: //initially rotate to align with target location

                if(true) { //rotateIMU(heading,1)) {
                    fieldPosState++;
                }
                break;
            case 1:
                double distance = getDistanceTo(targetX, targetY);

                //this seems to be a way to slow down when close to a target location
                //todo: better PID pased position closing
                //double power =  distance < 0.1524 ? 0 : 0.5;
                //double maxPower = .5; // (as a magnituded)

                if(driveAbsoluteDistance(maxPower, heading, forward, distance, closeEnoughDist)) {
                    fieldPosState = 0;
                    return true;
                }
                break;
        }
        return false;
    }

    int fieldPosStateToo = 0;
    // drive with a final heading
        public boolean driveToFieldPosition(double targetX, double targetY, boolean forward, double maxPower, double closeEnoughDist, double targetFinalHeading){
        switch (fieldPosStateToo){
            case 0:
                if(driveToFieldPosition(targetX, targetY, forward, maxPower, closeEnoughDist)) {
                    fieldPosStateToo++;
                }
                break;
            case 1:
                if(rotateIMU(targetFinalHeading, 5)){
                    fieldPosStateToo = 0;
                    return true;
                }
        }
        return false;
    }

        int getFieldPosStateThree = 0;
        double launchMoveDist = 0.0; // the distance at which launcher movements should start
        //drive to a fully defined Position
    public boolean driveToFieldPosition(Constants.Position targetPose, boolean forward, double maxPower, double closeEnoughDist){
        switch (getFieldPosStateThree){
            case 0:
                //calc the distance remaining when the launcher movements should kick-in
                launchMoveDist = getDistanceTo(targetPose.x, targetPose.y);
                launchMoveDist-= launchMoveDist * targetPose.launchStart;

                getFieldPosStateThree++;

                break;
            case 1: //driving to target location
                if(getDistanceTo((Constants.ALLIANCE_INT_MOD) * targetPose.x, targetPose.y) <= launchMoveDist){

                }

                if(targetPose.baseHeading > -.01) {
                    if (driveToFieldPosition((Constants.ALLIANCE_INT_MOD) * targetPose.x, targetPose.y, forward, maxPower, closeEnoughDist, targetPose.baseHeading)) {
                        getFieldPosStateThree++;
                    }
                }
                else{
                    if (driveToFieldPosition((Constants.ALLIANCE_INT_MOD) * targetPose.x, targetPose.y, forward, maxPower, closeEnoughDist)) {
                        getFieldPosStateThree++;
                    }
                }
                break;
            case 2: //end of travel
                if(targetPose.launchStart > .99){}

                return true;

                }
        return false;
    }

    private double getDistanceTo(double targetX, double targetY) {
        return Math.sqrt(Math.pow((targetX-getPoseX()),2) + Math.pow((targetY-getPoseY()),2));
    }

    public double getBearingTo(double targetX, double targetY) {
        return Math.toDegrees(Math.atan2((targetX-getPoseX()), (targetY-getPoseY())));
    }

    public double getBearingToWrapped(double targetX, double targetY) {
        return wrap360(Math.toDegrees(Math.atan2((targetX-getPoseX()), (targetY-getPoseY()))));
    }

    // todo - All Articulations need to be rebuilt - most of these are from icarus
    // and will be removed
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    //// ////
    //// Articulations ////
    //// ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    double miniTimer;
    int miniState = 0;

    public boolean articulate(Articulation target, boolean setAndForget) {
        articulate(target);
        return true;
    }


    double autonTurnTarget = 90;
    public boolean cardinalBaseTurn(boolean isRightTurn) {
        if (rotateIMU(autonTurnTarget, 5.0) ) {
            autonTurnTarget = wrap360(isRightTurn ? autonTurnTarget + 90 : autonTurnTarget - 90);
            return true;
        }
        return false;
    }

    int returnHomeState = 0;
    public boolean returnHome(){
        switch(returnHomeState){
            case 0:
                if(rotateIMU(wrap360(360 - getBearingToWrapped(Constants.Position.HOME.x, Constants.Position.HOME.y)),5)){
                    returnHomeState++;
                }
                break;
            case 1:
                if(driveToFieldPosition(Constants.Position.HOME,false,  .3,.08)){
                    returnHomeState++;
                }
                break;
            case 2:
                returnHomeState = 0;
                return true;
        }
        return false;
    }

    public Articulation articulate(Articulation target) {
        articulation = target; // store the most recent explict articulation request as our target, allows us
                               // to keep calling incomplete multi-step transitions
        if (target == Articulation.manual) {
            miniState = 0; // reset ministate - it should only be used in the context of a multi-step
                           // transition, so safe to reset it here
        }

        //articulatuion backbone
        switch (articulation) {
            case manual:
                break; // do nothing here - likely we are directly overriding articulations in game
            case cardinalBaseLeft:
                if (cardinalBaseTurn(false)) {
                    articulation = Articulation.manual;
                }
                break;
            case cardinalBaseRight:
                if (cardinalBaseTurn(true)) {
                    articulation = Articulation.manual;
                }
                break;
            case returnHome:
                if (returnHome()) {
                    articulation = Articulation.manual;
                }
                break;
            default:
                return target;

        }
        return target;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    ////                                                                                  ////
    ////                                Platform Movements                                ////
    ////                                                                                  ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Stops all motors on the robot
     */
    public void stopAll() {
        driveMixerTrike(0, 0);
    }

    public void stopChassis() {
        driveMixerTrike(0, 0);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    ////                                                                                  ////
    ////                           Drive Platform Mixing Methods                          ////
    ////                                                                                  ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////


    double lengthOfRobot = 1.4; //todo- needs to be updated on every loop

    double newCalcDistForward = 0; // normalized input values to pass into the calculator
    double newRotateAmount = 0;

    TrikeChassisMovementCalc chassisCalc = new TrikeChassisMovementCalc(0,0,0, 0,0); //todo- change the input length to the starting length

    public void driveMixerTrike(double forward, double rotate){
        //normalize inputs
        newCalcDistForward = forward * Constants.maxForwardSpeed * loopTime;
        newRotateAmount = rotate * Constants.maxRotateSpeed * loopTime;

        //updates the calc, triggers re-do of calcs
        chassisCalc.updateVals(0,newCalcDistForward, newRotateAmount, lengthOfRobot, lengthOfRobot); //todo- change the last variable to something else

        //set the motor speed to the calc's returned speed
        motorFrontLeft.setVelocity(chassisCalc.getRelMotorVels(loopTime)[0], AngleUnit.RADIANS);
        motorFrontRight.setVelocity(chassisCalc.getRelMotorVels(loopTime)[1], AngleUnit.RADIANS);
        motorMiddle.setVelocity(chassisCalc.getRelMotorVels(loopTime)[2], AngleUnit.RADIANS);

        turnSwervePID(swerveP, swerveI, swerveD, angleOfBackWheel(), chassisCalc.getNewSwerveAngle());
    }

    public void driveMixerTrike2(double forward, double rotate) {
        double length = Constants.CHASSIS_LENGTH, radius = 0, angle = 0;
        if(rotate == 0)
            angle = 0;
        else {
            radius = forward / rotate;
            angle = Math.atan2(length, radius);
        }

        double vl = forward - rotate * (rotate == 0 ? 0 : radius - Constants.TRACK_WIDTH / 2);
        double vr = forward + rotate * (rotate == 0 ? 0 : radius + Constants.TRACK_WIDTH / 2);
        double v_swerve = forward + rotate * (rotate == 0 ? 0 : Math.hypot(radius, getChassisLength()));

        motorFrontLeft.setVelocity(vl);
        motorFrontRight.setVelocity(vr);
        motorMiddle.setVelocity(v_swerve);

        turnSwervePID(swerveP, swerveI, swerveD, angleOfBackWheel(), angle);
    }

    public double getChassisLength() {
        return chassisLengthSensor.getDistance(DistanceUnit.METER);
    }



    public double angleOfBackWheel(){
        return motorMiddleSwivel.getCurrentPosition() / Constants.swerveAngleTicksPerDegree;
    }

    public void turnSwervePID(double Kp, double Ki, double Kd, double currentAngle, double targetAngle){

        //initialization of the PID calculator's output range, target value and multipliers
        swervePID.setOutputRange(-.69, .69); //this is funny
        swervePID.setPID(Kp, Ki, Kd);
        swervePID.setSetpoint(targetAngle);
        swervePID.enable();

        //initialization of the PID calculator's input range and current value
        swervePID.setInputRange(0, 360);
        swervePID.setContinuous();
        swervePID.setInput(currentAngle);

        turnError = diffAngle2(targetAngle, currentAngle);

        //calculates the angular correction to apply
        double correction = swervePID.performPID();

        //performs the turn with the correction applied
        motorMiddleSwivel.setPower(correction);
    }

    public static void normalize(double[] motorspeeds) {
        double max = Math.abs(motorspeeds[0]);
        for (int i = 0; i < motorspeeds.length; i++) {
            double temp = Math.abs(motorspeeds[i]);
            if (max < temp) {
                max = temp;
            }
        }
        if (max > 1) {
            for (int i = 0; i < motorspeeds.length; i++) {
                motorspeeds[i] = motorspeeds[i] / max;
            }
        }
    }



    /**
     * Reset the encoder readings on all drive motors
     * 
     * @param enableEncoders if true, the motors will continue to have encoders
     *                       active after reset
     */
    public void resetMotors(boolean enableEncoders) {
        motorFrontLeft.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motorFrontRight.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motorMiddle.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motorMiddleSwivel.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        if (enableEncoders) {
             motorFrontLeft.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
             motorFrontRight.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            motorMiddle.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            motorMiddleSwivel.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        } else {
             motorFrontLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
             motorFrontRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motorMiddle.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motorMiddleSwivel.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    ////                                                                                  ////
    ////                   Drive Platform Differential Mixing Methods                     ////
    ////                                                                                  ////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    public double clampMotor(double power) {
        return clampDouble(-1, 1, power);
    }

    /**
     * clamp a double value to put it within a given range
     * 
     * @param min   lower bound of the range
     * @param max   upper bound of the range
     * @param value double being clamped to the given range
     */
    public double clampDouble(double min, double max, double value) {
        double result = value;
        if (value > max)
            result = max;
        if (value < min)
            result = min;
        return result;
    }

    // todo - these getAverageTicks are suspicious - it's not the best way to
    // approximate travel - prefer to use a separate position sensor than the wheels
    // - particularly with mecanums

    /**
     * retrieve the average value of ticks on all motors - differential
     */
    public long getAverageTicks() {
        long averageTicks = (motorFrontRight.getCurrentPosition() + motorFrontLeft.getCurrentPosition()) / 2;
        return averageTicks;
    }

    /**
     * retrieve the average of the absolute value of ticks on all motors - mecanum
     */
    public long getAverageAbsTicks() {
        long averageTicks = (Math.abs(motorFrontLeft.getCurrentPosition())
                + Math.abs(motorMiddle.getCurrentPosition()) + Math.abs(motorFrontRight.getCurrentPosition())) / 3;
        return averageTicks;
    }

    public int getFrontLeftMotorTicks() {
        return motorFrontLeft.getCurrentPosition();
    }

    public int getFrontRightMotorTicks() {
        return motorFrontRight.getCurrentPosition();
    }

    public int getMiddleMotorTicks() {
        return motorMiddle.getCurrentPosition();
    }


    /**
     * Moves the tank platform under PID control applied to the rotation of the
     * robot. This version can either drive forwards/backwards or strafe.
     *
     * @param Kp           proportional constant multiplier
     * @param Ki           integral constant multiplier
     * @param Kd           derivative constant multiplier
     * @param pwr          base motor power before correction is applied
     * @param currentAngle current angle of the robot in the coordinate system of
     *                     the sensor that provides it- should be updated every
     *                     cycle
     * @param targetAngle  the target angle of the robot in the coordinate system of
     *                     the sensor that provides the current angle
     */
    // this version is for differential steer robots
    public void movePID(double Kp, double Ki, double Kd, double pwr, double currentAngle, double targetAngle) {

        movePIDMixer(Kp, Ki, Kd, pwr, 0, currentAngle, targetAngle);
    }

    /**
     * Moves the mecanum platform under PID control applied to the rotation of the
     * robot. This version can either drive forwards/backwards or strafe.
     *
     * @param Kp           proportional constant multiplier
     * @param Ki           integral constant multiplier
     * @param Kd           derivative constant multiplier
     * @param pwr          base motor power before correction is applied
     * @param currentAngle current angle of the robot in the coordinate system of
     *                     the sensor that provides it- should be updated every
     *                     cycle
     * @param targetAngle  the target angle of the robot in the coordinate system of
     *                     the sensor that provides the current angle
     * @param strafe       if true, the robot will drive left/right. if false, the
     *                     robot will drive forwards/backwards.
     */
    public void movePID(double Kp, double Ki, double Kd, double pwr, double currentAngle, double targetAngle,
            boolean strafe) {

        if (strafe)
            movePIDMixer(Kp, Ki, Kd, 0, pwr, currentAngle, targetAngle);
        else
            movePIDMixer(Kp, Ki, Kd, pwr, 0, currentAngle, targetAngle);

    }

    /**
     *
     * Moves the chassis under PID control applied to the
     * heading and speed of the robot with support for mecanum drives.
     * This version can drive forwards/backwards and strafe
     * simultaneously. If called with strafe set to zero, this can be used for normal
     * 4 motor or 2 motor differential drive robots
     *
     * @param maxPwrFwd       base forwards/backwards motor power before correction is
     *                     applied
     * @param dist         the distance in meters still to go - this is effectively the distance error
     * @param pwrStf       base left/right motor power before correction is applied
     * @param currentAngle current angle of the robot in the coordinate system of
     *                     the sensor that provides it- should be updated every
     *                     cycle
     * @param targetAngle  the target angle of the robot in the coordinate system of
     *                     the sensor that provides the current angle
     */
    public void movePID(double maxPwrFwd, boolean forward, double dist, double pwrStf, double currentAngle,
                        double targetAngle) {

        // setup turnPID
        turnPID.setOutputRange(-.5, .5);
        turnPID.setIntegralCutIn(cutout);
        turnPID.setPID(driveP, turnI, turnD);
        turnPID.setSetpoint(targetAngle);
        turnPID.setInputRange(0, 360);
        turnPID.setContinuous();
        turnPID.setInput(currentAngle);
        turnPID.enable();

        // setup distPID
        distPID.setOutputRange(-maxPwrFwd, maxPwrFwd);
        distPID.setIntegralCutIn(cutout); //todo - cutout was for turning and probably needs to be changed for distance if integral is needed
        distPID.setPID(distP, distI, distD);
        distPID.setSetpoint(dist); //trying to get to a zero distance
        distPID.setInput(0);
        distPID.enable();

        // calculate the angular correction to apply
        double turnCorrection = turnPID.performPID();
        // calculate chassis power
        double basePwr = distPID.performPID();
        if (!forward) basePwr *=-1;

        // performs the drive with the correction applied
        driveMixerTrike(basePwr, turnCorrection);
    }


    /**
     * OLD METHOD - only applies  PID to turns, not base power
     * Moves the omnidirectional (mecanum) platform under PID control applied to the
     * rotation of the robot. This version can drive forwards/backwards and strafe
     * simultaneously. If called with strafe set to zero, this will work for normal
     * 4 motor differential robots
     *
     * @param Kp           proportional constant multiplier
     * @param Ki           integral constant multiplier
     * @param Kd           derivative constant multiplier
     * @param pwrFwd       base forwards/backwards motor power before correction is
     *                     applied
     * @param pwrStf       base left/right motor power before correction is applied
     * @param currentAngle current angle of the robot in the coordinate system of
     *                     the sensor that provides it- should be updated every
     *                     cycle
     * @param targetAngle  the target angle of the robot in the coordinate system of
     *                     the sensor that provides the current angle
     */

    double turnError = 0;

    public void movePIDMixer(double Kp, double Ki, double Kd, double pwrFwd, double pwrStf, double currentAngle,
            double targetAngle) {
        // if (pwr>0) PID.setOutputRange(pwr-(1-pwr),1-pwr);
        // else PID.setOutputRange(pwr - (-1 - pwr),-1-pwr);

        // initialization of the PID calculator's output range, target value and
        // multipliers
        turnPID.setOutputRange(-.3, .3);
        turnPID.setIntegralCutIn(cutout);
        turnPID.setPID(Kp, Ki, Kd);
        turnPID.setSetpoint(targetAngle);
        turnPID.enable();

        // initialization of the PID calculator's input range and current value
        turnPID.setInputRange(0, 360);
        turnPID.setContinuous();
        turnPID.setInput(currentAngle);


        // calculates the angular correction to apply
        double turnCorrection = turnPID.performPID();
        turnError = wrapAngleMinus(targetAngle, currentAngle);

        // performs the drive with the correction applied
        driveMixerTrike(pwrFwd, turnCorrection);

        // logging section that can be reactivated for debugging
        /*
         * ArrayList toUpdate = new ArrayList(); toUpdate.add((PID.m_deltaTime));
         * toUpdate.add(Double.valueOf(PID.m_error)); toUpdate.add(new
         * Double(PID.m_totalError)); toUpdate.add(new Double(PID.pwrP));
         * toUpdate.add(new Double(PID.pwrI)); toUpdate.add(new Double(PID.pwrD));
         */
        /*
         * logger.UpdateLog(Long.toString(System.nanoTime()) + "," +
         * Double.toString(PID.m_deltaTime) + "," + Double.toString(pose.getOdometer())
         * + "," + Double.toString(PID.m_error) + "," +
         * Double.toString(PID.m_totalError) + "," + Double.toString(PID.pwrP) + "," +
         * Double.toString(PID.pwrI) + "," + Double.toString(PID.pwrD) + "," +
         * Double.toString(correction)); motorLeft.setPower(pwr + correction);
         * motorRight.setPower(pwr - correction);
         */
    }

    public void movegenericPIDMixer(double Kp, double Ki, double Kd, double pwrFwd, double pwrStf, double currentVal, double targetVal) {
        // if (pwr>0) PID.setOutputRange(pwr-(1-pwr),1-pwr);
        // else PID.setOutputRange(pwr - (-1 - pwr),-1-pwr);

        // initialization of the PID calculator's output range, target value and
        // multipliers
        turnPID.setOutputRange(-.5, .5);
        turnPID.setIntegralCutIn(cutout);
        turnPID.setPID(Kp, Ki, Kd);
        turnPID.setSetpoint(targetVal);
        turnPID.enable();

        // initialization of the PID calculator's input range and current value
        turnPID.setContinuous();
        turnPID.setInput(currentVal);

        // calculates the angular correction to apply
        double correction = turnPID.performPID();

        // performs the drive with the correction applied
        driveMixerTrike(pwrFwd, correction);

        // logging section that can be reactivated for debugging
        /*
         * ArrayList toUpdate = new ArrayList(); toUpdate.add((PID.m_deltaTime));
         * toUpdate.add(Double.valueOf(PID.m_error)); toUpdate.add(new
         * Double(PID.m_totalError)); toUpdate.add(new Double(PID.pwrP));
         * toUpdate.add(new Double(PID.pwrI)); toUpdate.add(new Double(PID.pwrD));
         */
        /*
         * logger.UpdateLog(Long.toString(System.nanoTime()) + "," +
         * Double.toString(PID.m_deltaTime) + "," + Double.toString(pose.getOdometer())
         * + "," + Double.toString(PID.m_error) + "," +
         * Double.toString(PID.m_totalError) + "," + Double.toString(PID.pwrP) + "," +
         * Double.toString(PID.pwrI) + "," + Double.toString(PID.pwrD) + "," +
         * Double.toString(correction)); motorLeft.setPower(pwr + correction);
         * motorRight.setPower(pwr - correction);
         */
    }

    public void driveDriftCorrect(double driftance, double origialDist, double pwr, boolean forward) {
        double correctionAngle;
        if (forward)
            correctionAngle = Math.acos(origialDist / driftance);
        else
            correctionAngle = 360.0 - Math.acos(origialDist / driftance);
        driveIMUDistance(pwr, correctionAngle, forward, Math.sqrt(Math.pow(origialDist, 2) + Math.pow(driftance, 2)));
    }

    /**
     * Drive forwards for a set power while maintaining an IMU heading using PID
     * 
     * @param Kp          proportional multiplier for PID
     * @param Ki          integral multiplier for PID
     * @param Kd          derivative proportional for PID
     * @param pwr         set the forward power
     * @param targetAngle the heading the robot will try to maintain while driving
     */
    // this version is for omnidirectional robots
    public void driveIMU(double Kp, double Ki, double Kd, double pwr, double targetAngle, boolean strafe) {
        movePID(Kp, Ki, Kd, pwr, poseHeading, targetAngle, strafe);
    }

    /**
     * Drive with a set power for a set distance while maintaining an IMU heading
     * using PID
     * 
     * @param Kp            proportional multiplier for PID
     * @param pwr           set the forward power
     * @param targetAngle   the heading the robot will try to maintain while driving
     * @param forwardOrLeft is the robot driving in the forwards/left (positive)
     *                      directions or backwards/right (negative) directions
     * @param targetMeters  the target distance (in meters)
     * @param strafe        tells if the robot driving forwards/backwards or
     *                      left/right
     */
    public boolean driveIMUDistance(double Kp, double pwr, double targetAngle, boolean forwardOrLeft,
            double targetMeters, boolean strafe) {

        // set what direction the robot is supposed to be moving in for the purpose of
        // the field position calculator
        if (!forwardOrLeft) {
            moveMode = moveMode.backward;
            targetMeters = -targetMeters;
            pwr = -pwr;
        } else
            moveMode = moveMode.forward;

        // calculates the target position of the drive motors
        long targetPos;
        if (strafe)
            targetPos = (long) targetMeters * strafeTPM;
        else
            targetPos = (long) (targetMeters * forwardTPM);

        // if this statement is true, then the robot has not achieved its target
        // position
        if (Math.abs(targetPos) < Math.abs(getAverageAbsTicks())) {
            driveIMU(Kp, turnI, turnD, pwr, targetAngle, strafe);
            return false;
        }

        // destination achieved
        else {
            driveMixerTrike(0, 0);
            return true;
        }
    }

    /**
     * Rotate to a specific heading with a time cutoff in case the robot gets stuck
     * and cant complete the turn otherwise
     * 
     * @param targetAngle the heading the robot will attempt to turn to
     * @param maxTime     the maximum amount of time allowed to pass before the
     *                    sequence ends
     */
    public boolean rotateIMU(double targetAngle, double maxTime) {
        if (!turnTimerInit) { // intiate the timer that the robot will use to cut of the sequence if it takes
                              // too long; only happens on the first cycle
            turnTimer = System.nanoTime() + (long) (maxTime * (long) 1e9);
            turnTimerInit = true;
        }
        driveIMU(turnP, turnI, turnD, 0, targetAngle, false); // check to see if the robot turns within a
                                                                    // threshold of the target
         if(Math.abs(getHeading() - targetAngle) < minTurnError && Math.abs(rotVelBase) < 20) {
         turnTimerInit = false;
         driveMixerTrike(0,0);
         return true;
         }

        if (turnTimer < System.nanoTime()) { // check to see if the robot takes too long to turn within a threshold of
                                             // the target (e.g. it gets stuck)
            turnTimerInit = false;
            driveMixerTrike(0,  0);
            return true;
        }
        return false;
    }


    public static double getPoseX() { //todo-get rid of once done
        return poseX;
    }

    public static double getPoseY() {
        return poseY;
    }

    public static double getDisplacement() {
        return displacement;
    }


    /**
     * the maintain heading function used in demo: holds the heading read on initial
     * button press
     * 
     * @param buttonState the active state of the button; if true, hold the current
     *                    position. if false, do nothing
     */
    public void maintainHeading(boolean buttonState) {

        // if the button is currently down, maintain the set heading
        if (buttonState) {
            // if this is the first time the button has been down, then save the heading
            // that the robot will hold at and set a variable to tell that the heading has
            // been saved
            if (!maintainHeadingInit) {
                poseSavedHeading = poseHeading;
                maintainHeadingInit = true;
            }
            // hold the saved heading with PID
            driveIMU(turnP, turnI, turnD, 0, poseSavedHeading, false);
        }

        // if the button is not down, set to make sure the correct heading will be saved
        // on the next button press
        if (!buttonState) {
            maintainHeadingInit = false;
        }
    }

    /**
     * assign the current heading of the robot to zero
     */
    public void setZeroHeading() {
        setHeading(0);
    }

    /**
     * assign the current heading of the robot to a specific angle
     * 
     * @param angle the value that the current heading will be assigned to
     */
    public void setHeading(double angle) {
        poseHeading = angle;
        initialized = false; // triggers recalc of heading offset at next IMU update cycle
    }

    public void resetTPM() {
        forwardTPM = 2493;
    }

    /**
     * sets autonomous imu offset for turns
     */
    public void setAutonomousIMUOffset(double offset) {
        autonomousIMUOffset = offset;
    }

    /**
     * Set the current position of the robot in the X direction on the field
     * 
     * @param poseX
     */
    public void setPoseX(double poseX) {
        this.poseX = poseX;
    }

    /**
     * Set the current position of the robot in the Y direction on the field
     * 
     * @param poseY
     */
    public void setPoseY(double poseY) {
        this.poseY = poseY;
    }

    /**
     * Set the absolute heading (yaw) of the robot _0-360 degrees
     * 
     * @param poseHeading
     */
    public void setPoseHeading(double poseHeading) {
        this.poseHeading = poseHeading;
        initialized = false; // trigger recalc of offset on next update
    }

    /**
     * Set the absolute pitch of the robot _0-360 degrees
     * 
     * @param posePitch
     */
    public void setPosePitch(double posePitch) {
        this.posePitch = posePitch;
        initialized = false; // trigger recalc of offset on next update
    }

    /**
     * Set the absolute roll of the robot _0-360 degrees
     * 
     * @param poseRoll
     */
    public void setPoseRoll(double poseRoll) {
        this.poseRoll = poseRoll;
        initialized = false; // trigger recalc of offset on next update
    }

    /**
     * Returns the x position of the robot
     *
     * @return The current x position of the robot
     */
    public double getX() {
        return poseX;
    }

    /**
     * Returns the y position of the robot
     *
     * @return The current y position of the robot
     */
    public double getY() {
        return poseY;
    }

    /**
     * Returns the angle of the robot
     *
     * @return The current angle of the robot
     */
    public double getHeading() {
        return poseHeading;
    }

    public double getHeadingRaw() {
        return imuAngles.firstAngle;
    }

    /**
     * Returns the speed of the robot
     *
     * @return The current speed of the robot
     */
    public double getSpeed() {
        return poseSpeed;
    }

    public double getPitch() {
        return posePitch;
    }

    public double getRoll() {
        return poseRoll;
    }

    public long getForwardTPM() {
        return forwardTPM;
    }

    public void setForwardTPM(long forwardTPM) {
        this.forwardTPM = (int) forwardTPM;
    }

    /**
     *
     * gets the odometer. The odometer tracks the robot's total amount of travel
     * since the last odometer reset The value is in meters and is always increasing
     * (absolute), even when the robot is moving backwards
     * 
     * @returns odometer value
     */
    public double getOdometer() {

        return odometer;

    }

    /**
     * resets the odometer. The odometer tracks the robot's total amount of travel
     * since the last odometer reset The value is in meters and is always increasing
     * (absolute), even when the robot is moving backwards
     * 
     * @param distance
     */
    public void setOdometer(double distance) {
        odometer = 0;
    }

    public boolean returnTrue(){
        return true;
    }

    public boolean fortnight(){
        return false; //why are you here
    }


}
