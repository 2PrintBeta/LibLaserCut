/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Util;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;

/**
 *
 * @author Sven
 */
public class MakeBlockXYPlotter extends LaserCutter
{
  // TODO: 
  // send serial
  
  private enum ToolState {
    ON, OFF
  }
  
  /*
   * Internal Settings
  */
  private final boolean debug = false; // print to command line
  private static final String MODELNAME = "MakeBlockXYPlotter";
  private double addSpacePerRasterLine = 0.5;
  private String hostname = "file:///Users/Sven/Desktop/out.gcode";
  private double bedWidth = 300;
  private double bedHeight = 210;
  private int speedRate = 255;
  private int powerRate = 255;
  private String usedTool = "PEN"; // PEN, Laser
  private List<Double> resolutions = Arrays.asList(new Double[]{
                64d // fine liner
              });
  
  private int currentSpeed;
  private int currentPower;
  private ToolState toolState;
  
  private PrintWriter w = null;
  BufferedReader portReader = null;
  private BufferedOutputStream out = null;
  private SerialPort port = null;
    
  /*
   * Global Settings
  */
  private static final String SETTING_HOSTNAME = "Target port:// or file://";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per Raster line (mm)";
  private static final String SETTING_BEDWIDTH = "Laserbed width (mm)";
  private static final String SETTING_BEDHEIGHT = "Laserbed height (mm)";
  private static final String SETTING_SPEED_RATE = "Max. Speed Rate (abs. value)";
  private static final String SETTING_POWER_RATE = "Max. Power Rate (abs. value)";
  private static final String SETTING_TOOL = "Tool (PEN, LASER)";
  private static String[] settingAttributes = new String[]{
    SETTING_HOSTNAME,
    SETTING_RASTER_WHITESPACE,
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_SPEED_RATE,
    SETTING_POWER_RATE,
    SETTING_TOOL
  };
  
  
  /**
   * Get the value of MODELNAME
   * 
   * @return the value of MODELNAME
   */
  @Override
  public String getModelName() {
    return MODELNAME;
  }
  
  @Override
  public List<Double> getResolutions() {
    return resolutions;
  }
  
  @Override
  public MakeBlockXYPlotterProperty getLaserPropertyForVectorPart() {
    return new MakeBlockXYPlotterProperty(this.usedTool.equals("LASER")); // show power and speed only if laser
  }

  @Override
  public MakeBlockXYPlotterProperty getLaserPropertyForRasterPart()
  {
    return new MakeBlockXYPlotterProperty(this.usedTool.equals("LASER")); // show power and speed only if laser
  }
  
  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth()
  {
    return bedWidth;
  }

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  
  private void generateInitializationGCode() throws Exception {
    this.send("G54");//use table offset
    this.send("G21");//units to mm
    this.send("G90");//following coordinates are absolute
    toolOff();
    this.send("G28 X Y");//move to 0 0
  }

  private void generateShutdownGCode() throws Exception {
    //back to origin and shutdown
    toolOff();
    this.send("G28 X Y");//move to 0 0
  }
  
  private void toolOff() throws Exception {
    if(toolState != ToolState.OFF) {
      if(usedTool.equals("PEN")) {
        this.send("M1 90");
      } else if(usedTool.equals("LASER")) {
        this.send("M1 ???");
      } else {
        throw new Exception("Tool " + this.usedTool + " not supported!");
      }
        toolState = ToolState.OFF;
    }
  }
  
  private void toolOn() throws Exception {
    if(toolState != ToolState.ON) {
      if(usedTool.equals("PEN")) {
        this.send("M1 130");
      } else if(usedTool.equals("LASER")) {
        this.send("M1 ???");
      } else {
        throw new Exception("Tool " + this.usedTool + " not supported!");
      }
      toolState = ToolState.ON;
    }
  }
  
  private void setSpeed(int value) throws Exception{
    if(usedTool.equals("LASER")) {
      if (value != currentSpeed) {
        this.send(String.format(Locale.US, "G1 F%d", (int) ((double) speedRate * value / 100)));
        currentSpeed = value;
      }
    }
  }
  
  private void setPower(int value) throws Exception{
    if(usedTool.equals("LASER")) {
      if (value != currentPower) {
        this.send(String.format(Locale.US, "S%d", (int) ((double) powerRate * value / 100)));
        currentPower = value;
      }
    }
  }
  
  private void move(int x, int y, double resolution) throws Exception{
    toolOff();
    this.send(String.format(Locale.US, "G0 X%f Y%f", Util.px2mm(x, resolution), Util.px2mm(y, resolution)));
  }

  private void line(int x, int y, double resolution) throws Exception{
    toolOn();
    this.send(String.format(Locale.US, "G1 X%f Y%f", Util.px2mm(x, resolution), Util.px2mm(y, resolution)));
  }
  
  private void generateVectorGCode(VectorPart vp, double resolution, ProgressListener pl, int startProgress, int maxProgress) throws UnsupportedEncodingException, Exception {
    int i = 0;
    int progress;
    int max = vp.getCommandList().length;
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        case MOVETO:
          int x = cmd.getX();
          int y = cmd.getY();
          this.move(x, y, resolution);
          break;
        case LINETO:
          x = cmd.getX();
          y = cmd.getY();
          this.line(x, y, resolution);
          break;
        case SETPROPERTY:
          PowerSpeedFocusFrequencyProperty p = (PowerSpeedFocusFrequencyProperty) cmd.getProperty();
          this.setPower(p.getPower());
          this.setSpeed(p.getSpeed());
          break;
      }
      i++;
      progress = (startProgress + (int) (i*(double) maxProgress/max));
      pl.progressChanged(this, progress);
    }
  }
  
  private void generatePseudoRasterGCode(RasterPart rp, double resolution, ProgressListener pl, int startProgress, int maxProgress) throws UnsupportedEncodingException, Exception {
    int i = 0;
    int progress;
    int max = rp.getRasterHeight();
    
    boolean dirRight = true;
    Point rasterStart = rp.getRasterStart();
    PowerSpeedFocusProperty prop = (PowerSpeedFocusProperty) rp.getLaserProperty();
    this.setSpeed(prop.getSpeed());
    this.setPower(prop.getPower());
    for (int line = 0; line < rp.getRasterHeight(); line++) {
      Point lineStart = rasterStart.clone();
      lineStart.y += line;
      List<Byte> bytes = new LinkedList<Byte>();
      boolean lookForStart = true;
      for (int x = 0; x < rp.getRasterWidth(); x++) {
        if (lookForStart) {
          if (rp.isBlack(x, line)) {
            lookForStart = false;
            bytes.add((byte) 255);
          } else {
            lineStart.x += 1;
          }
        } else {
          bytes.add(rp.isBlack(x, line) ? (byte) 255 : (byte) 0);
        }
      }
      //remove trailing zeroes
      while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0) {
        bytes.remove(bytes.size() - 1);
      }
      if (bytes.size() > 0) {
        if (dirRight) {
          //add some space to the left
          this.move(Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          //move to the first nonempyt point of the line
          this.move(lineStart.x, lineStart.y, resolution);
          byte old = bytes.get(0);
          for (int pix = 0; pix < bytes.size(); pix++) {
            if (bytes.get(pix) != old) {
              if (old == 0) {
                this.move(lineStart.x + pix, lineStart.y, resolution);
              } else {
                this.setPower(prop.getPower() * (0xFF & old) / 255);
                this.line(lineStart.x + pix - 1, lineStart.y, resolution);
                this.move(lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          this.setPower(prop.getPower() * (0xFF & bytes.get(bytes.size() - 1)) / 255);
          this.line(lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          //add some space to the right
          this.move(Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
        } else {
          //add some space to the right
          this.move(Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          //move to the last nonempty point of the line
          this.move(lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          byte old = bytes.get(bytes.size() - 1);
          for (int pix = bytes.size() - 1; pix >= 0; pix--) {
            if (bytes.get(pix) != old || pix == 0) {
              if (old == 0) {
                this.move(lineStart.x + pix, lineStart.y, resolution);
              } else {
                this.setPower(prop.getPower() * (0xFF & old) / 255);
                this.line(lineStart.x + pix + 1, lineStart.y, resolution);
                this.move(lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          this.setPower(prop.getPower() * (0xFF & bytes.get(0)) / 255);
          this.line(lineStart.x, lineStart.y, resolution);
          //add some space to the left
          this.move(Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
        }
      }
      dirRight = !dirRight;
      
      i = line + 1;
      progress = (startProgress + (int) (i*(double) maxProgress/max));
      pl.progressChanged(this, progress);
    }
  }
  
  private void connect() throws NoSuchPortException, PortInUseException, Exception {
    if(!this.debug){
      if (this.hostname.startsWith("port://")) {
        String portString = this.hostname.replace("port://", "");
        CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(portString);
        port = (SerialPort) cpi.open("VisiCut", 2000);
        if (port == null)
        {
          throw new Exception("Error: Could not Open COM-Port '"+portString+"'");
        }
        if (!(port instanceof SerialPort))
        {
          throw new Exception("Port '"+portString+"' is not a serial port.");
        }
        port.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        out = new BufferedOutputStream(port.getOutputStream());
        portReader = new BufferedReader(new InputStreamReader(port.getInputStream()));
        
        String command = "\r\n\r\n";
        out.write(command.getBytes("US-ASCII"));
        out.flush();
        Thread.sleep(2000);
      }
      else if (hostname.startsWith("file://")) {
        String filename = this.hostname.replace("file://", "");
        try {
          w = new PrintWriter(filename);
        }
        catch(Exception e) {
          throw new Exception(String.format("No correct absolute file path: %s Exception %s", this.hostname, e));
        }
      }
      else {
        throw new Exception(String.format("Unknown hostname: %s", this.hostname));
      }
    }
  }
  
  private void disconnect() throws Exception{
    if(w != null) {
      w.close();
      w = null;
    }
    
    if(out != null) {
      out.close();
      out = null;
    }
    
    if(port != null){
      port.close();
      port = null;
    }
  }
  
  private void send(String command) throws Exception {
    if(!debug) {
      if (this.hostname.startsWith("port://")) {
        String sendString = command + "\n";
        out.write(sendString.getBytes("US-ASCII"));
        out.flush();
        this.waitForResponse(command);
      }
      else if (hostname.startsWith("file://")) {
        w.println(command);
      }
      else {
        throw new Exception(String.format("Unknown hostname: %s", this.hostname));
      }
      
    } else {
      System.out.println(command);
    }
  }
  
  private void waitForResponse(String command) throws IOException, Exception
  {
    String line;
    String expected = "ok";
    try {
      line = portReader.readLine();
      line = line.replace("\n", "").replace("\r", "");
      if(!line.toLowerCase().equals(expected.toLowerCase())) {
        throw new Exception(String.format("Got wrong response to command: %s:%s", command, line));
      }
      else {
        return; // everything ok
      }
    } catch(IOException e) { 
      throw new Exception("IO Exception, e.g. timeout");
    }
  }
  
  
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    this.currentPower = -1;
    this.currentSpeed = -1;
    this.toolState = ToolState.OFF;
    pl.progressChanged(this, 0);
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting");
    this.connect();
    pl.taskChanged(this, "sending");
    this.generateInitializationGCode();
    int startProgress = 20;
    pl.progressChanged(this, startProgress);
    int i = 0;
    int progress = startProgress;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof Raster3dPart)
      {
        throw new Exception("Raster 3D parts are not implemented for " + this.getModelName());
      }
      else if (p instanceof RasterPart)
      {
        this.generatePseudoRasterGCode((RasterPart) p, p.getDPI(), pl, progress, ((int) ((i+1)*(double) 80/max)));
      }
      else if (p instanceof VectorPart)
      {
        this.generateVectorGCode((VectorPart) p, p.getDPI(), pl, progress, ((int) ((i+1)*(double) 80/max)));
      }
      i++;
      progress = (startProgress + (int) (i*(double) 80/max));
      pl.progressChanged(this, progress);
    }
    this.generateShutdownGCode();
    pl.taskChanged(this, "disconnecting");
    this.disconnect();
    pl.taskChanged(this, "sent");
    pl.progressChanged(this, 100);
  }

  @Override
  public LaserCutter clone()
  {
    MakeBlockXYPlotter clone = new MakeBlockXYPlotter();
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    clone.hostname = hostname;
    clone.bedWidth = bedWidth;
    clone.bedHeight = bedHeight;
    clone.speedRate = speedRate;
    clone.powerRate = powerRate;
    clone.usedTool = usedTool;
    return clone;
  }

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOSTNAME.equals(attribute)) {
      return this.hostname;
    } else if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      return this.addSpacePerRasterLine;
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.bedWidth;
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.bedHeight;
    } else if (SETTING_SPEED_RATE.equals(attribute)) {
      return this.speedRate;
    } else if (SETTING_POWER_RATE.equals(attribute)) {
      return this.powerRate;
    } else if (SETTING_TOOL.equals(attribute)) {
      return this.usedTool;
    } 
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOSTNAME.equals(attribute)) {
      this.hostname = (String) value;
    } else if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      this.addSpacePerRasterLine = (Double) value;
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.bedWidth = (Double) value;
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.bedHeight = (Double) value;
    } else if (SETTING_SPEED_RATE.equals(attribute)) {
      this.speedRate = (Integer) value;
    } else if (SETTING_POWER_RATE.equals(attribute)) {
      this.powerRate = (Integer) value;
    } else if (SETTING_TOOL.equals(attribute)) {
      this.usedTool = (String) value;
    }  
  }
  
}
