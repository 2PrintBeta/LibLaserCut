/**
 * This file is part of VisiCut. Copyright (C) 2011 Thomas Oster
 * <thomas.oster@rwth-aachen.de> RWTH Aachen University - 52062 Aachen, Germany
 *
 * VisiCut is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * VisiCut is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with VisiCut. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Util;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * This class implements a driver for the LAOS Lasercutter board. Currently it
 * supports the simple code and the G-Code, which may be used in the future.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class Lasersaur extends LaserCutter {

  private static final String SETTING_COMPORT = "COM-Port/Device";
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String SETTING_FLIPX = "X axis goes right to left (yes/no)";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per Raster line (mm)";
  private static final String SETTING_SEEK_RATE = "Max. Seek Rate (mm/min)";
  private static final String SETTING_LASER_RATE = "Max. Laser Rate (mm/min)";

  @Override
  public String getModelName() {
    return "Lasersaur";
  }
  private double addSpacePerRasterLine = 0.5;

  /**
   * Get the value of addSpacePerRasterLine
   *
   * @return the value of addSpacePerRasterLine
   */
  public double getAddSpacePerRasterLine() {
    return addSpacePerRasterLine;
  }

  /**
   * Set the value of addSpacePerRasterLine
   *
   * @param addSpacePerRasterLine new value of addSpacePerRasterLine
   */
  public void setAddSpacePerRasterLine(double addSpacePerRasterLine) {
    this.addSpacePerRasterLine = addSpacePerRasterLine;
  }
  private double seekRate = 2000;

  /**
   * Get the value of seekRate
   *
   * @return the value of seekRate
   */
  public double getSeekRate() {
    return seekRate;
  }

  /**
   * Set the value of seekRate
   *
   * @param seekRate new value of seekRate
   */
  public void setSeekRate(double seekRate) {
    this.seekRate = seekRate;
  }
  private double laserRate = 2000;

  /**
   * Get the value of laserRate
   *
   * @return the value of laserRate
   */
  public double getLaserRate() {
    return laserRate;
  }

  /**
   * Set the value of laserRate
   *
   * @param laserRate new value of laserRate
   */
  public void setLaserRate(double laserRate) {
    this.laserRate = laserRate;
  }
  protected boolean flipXaxis = false;

  /**
   * Get the value of flipXaxis
   *
   * @return the value of flipXaxis
   */
  public boolean isFlipXaxis() {
    return flipXaxis;
  }

  /**
   * Set the value of flipXaxis
   *
   * @param flipXaxis new value of flipXaxis
   */
  public void setFlipXaxis(boolean flipXaxis) {
    this.flipXaxis = flipXaxis;
  }
  protected String comPort = "/dev/ttyUSB0";

  /**
   * Get the value of port
   *
   * @return the value of port
   */
  public String getComPort() {
    return comPort;
  }

  /**
   * Set the value of port
   *
   * @param comPort new value of port
   */
  public void setComPort(String comPort) {
    this.comPort = comPort;
  }

  private byte[] generateVectorGCode(VectorPart vp, int resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        case MOVETO:
          int x = cmd.getX();
          int y = cmd.getY();
          move(out, x, y, resolution);
          break;
        case LINETO:
          x = cmd.getX();
          y = cmd.getY();
          line(out, x, y, resolution);
          break;
        case SETPOWER:
          setPower(out, cmd.getPower());
          break;
        case SETFOCUS:
          float focus = cmd.getFocus();
          break;
        case SETSPEED:
          int speed = cmd.getSpeed();
          setSpeed(out, speed);
          break;
        case SETFREQUENCY:
          int frequency = cmd.getFrequency();
          break;
      }
    }
    return result.toByteArray();
  }
  private int currentPower = -1;
  private int currentSpeed = -1;

  private void setSpeed(PrintStream out, int speedInPercent) {
    if (speedInPercent != currentSpeed) {
      out.printf(Locale.US, "G1 F%i\n", (int) ((double) speedInPercent * this.getLaserRate() / 100));
      currentSpeed = speedInPercent;
    }

  }

  private void setPower(PrintStream out, int powerInPercent) {
    if (powerInPercent != currentPower) {
      out.printf(Locale.US, "S%i\n", (int) (255d * powerInPercent / 100));
      currentPower = powerInPercent;
    }
  }

  private void move(PrintStream out, int x, int y, double resolution) {
    out.printf(Locale.US, "G0 X%f Y%f\n", Util.px2mm(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), Util.px2mm(y, resolution));
  }

  private void line(PrintStream out, int x, int y, double resolution) {
    out.printf(Locale.US, "G1 X%f Y%f\n", Util.px2mm(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), Util.px2mm(y, resolution));
  }

  private byte[] generatePseudoRaster3dGCode(Raster3dPart rp, int resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    boolean dirRight = true;
    for (int raster = 0; raster < rp.getRasterCount(); raster++) {
      Point rasterStart = rp.getRasterStart(raster);
      LaserProperty prop = rp.getLaserProperty(raster);
      setSpeed(out, prop.getSpeed());
      for (int line = 0; line < rp.getRasterHeight(raster); line++) {
        Point lineStart = rasterStart.clone();
        lineStart.y += line;
        List<Byte> bytes = rp.getRasterLine(raster, line);
        //remove heading zeroes
        while (bytes.size() > 0 && bytes.get(0) == 0) {
          bytes.remove(0);
          lineStart.x += 1;
        }
        //remove trailing zeroes
        while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0) {
          bytes.remove(bytes.size() - 1);
        }
        if (bytes.size() > 0) {
          if (dirRight) {
            //move to the first nonempyt point of the line
            move(out, lineStart.x, lineStart.y, resolution);
            byte old = bytes.get(0);
            for (int pix = 0; pix < bytes.size(); pix++) {
              if (bytes.get(pix) != old) {
                if (old == 0) {
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                } else {
                  setPower(out, prop.getPower() * (0xFF & old) / 255);
                  line(out, lineStart.x + pix - 1, lineStart.y, resolution);
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                }
                old = bytes.get(pix);
              }
            }
            //last point is also not "white"
            setPower(out, prop.getPower() * (0xFF & bytes.get(bytes.size() - 1)) / 255);
            line(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          } else {
            //move to the last nonempty point of the line
            move(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
            byte old = bytes.get(bytes.size() - 1);
            for (int pix = bytes.size() - 1; pix >= 0; pix--) {
              if (bytes.get(pix) != old || pix == 0) {
                if (old == 0) {
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                } else {
                  setPower(out, prop.getPower() * (0xFF & old) / 255);
                  line(out, lineStart.x + pix + 1, lineStart.y, resolution);
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                }
                old = bytes.get(pix);
              }
            }
            //last point is also not "white"
            setPower(out, prop.getPower() * (0xFF & bytes.get(0)) / 255);
            line(out, lineStart.x, lineStart.y, resolution);
          }
        }
        dirRight = !dirRight;
      }
    }
    return result.toByteArray();
  }

  private byte[] generatePseudoRasterGCode(RasterPart rp, int resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    boolean dirRight = true;
    for (int raster = 0; raster < rp.getRasterCount(); raster++) {
      Point rasterStart = rp.getRasterStart(raster);
      LaserProperty prop = rp.getLaserProperty(raster);
      setSpeed(out, prop.getSpeed());
      setPower(out, prop.getPower());
      for (int line = 0; line < rp.getRasterHeight(raster); line++) {
        Point lineStart = rasterStart.clone();
        lineStart.y += line;
        //Convert BlackWhite line into line of 0 and 255 bytes
        BlackWhiteRaster bwr = rp.getImages()[raster];
        List<Byte> bytes = new LinkedList<Byte>();
        boolean lookForStart = true;
        for (int x = 0; x < bwr.getWidth(); x++) {
          if (lookForStart) {
            if (bwr.isBlack(x, line)) {
              lookForStart = false;
              bytes.add((byte) 255);
            } else {
              lineStart.x += 1;
            }
          } else {
            bytes.add(bwr.isBlack(x, line) ? (byte) 255 : (byte) 0);
          }
        }
        //remove trailing zeroes
        while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0) {
          bytes.remove(bytes.size() - 1);
        }
        if (bytes.size() > 0) {
          if (dirRight) {
            //add some space to the left
            move(out, Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
            //move to the first nonempyt point of the line
            move(out, lineStart.x, lineStart.y, resolution);
            byte old = bytes.get(0);
            for (int pix = 0; pix < bytes.size(); pix++) {
              if (bytes.get(pix) != old) {
                if (old == 0) {
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                } else {
                  setPower(out, prop.getPower() * (0xFF & old) / 255);
                  line(out, lineStart.x + pix - 1, lineStart.y, resolution);
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                }
                old = bytes.get(pix);
              }
            }
            //last point is also not "white"
            setPower(out, prop.getPower() * (0xFF & bytes.get(bytes.size() - 1)) / 255);
            line(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
            //add some space to the right
            move(out, Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          } else {
            //add some space to the right
            move(out, Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
            //move to the last nonempty point of the line
            move(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
            byte old = bytes.get(bytes.size() - 1);
            for (int pix = bytes.size() - 1; pix >= 0; pix--) {
              if (bytes.get(pix) != old || pix == 0) {
                if (old == 0) {
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                } else {
                  setPower(out, prop.getPower() * (0xFF & old) / 255);
                  line(out, lineStart.x + pix + 1, lineStart.y, resolution);
                  move(out, lineStart.x + pix, lineStart.y, resolution);
                }
                old = bytes.get(pix);
              }
            }
            //last point is also not "white"
            setPower(out, prop.getPower() * (0xFF & bytes.get(0)) / 255);
            line(out, lineStart.x, lineStart.y, resolution);
            //add some space to the left
            move(out, Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          }
        }
        dirRight = !dirRight;
      }
    }
    return result.toByteArray();
  }

  private byte[] generateInitializationCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    out.print("G54\n");//use table offset
    out.print("G21\n");//units to mm
    out.print("G90\n");//following coordinates are absolute
    out.print("G0 X0 Y0\n");//move to 0 0
    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    //back to origin and shutdown
    out.print("G0 X0 Y0\n");//move to 0 0
    return result.toByteArray();
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;
    BufferedOutputStream out;
    ByteArrayOutputStream buffer = null;
    pl.taskChanged(this, "checking job");
    checkJob(job);
    buffer = new ByteArrayOutputStream();
    out = new BufferedOutputStream(buffer);
    pl.taskChanged(this, "buffering");
    out.write(this.generateInitializationCode());
    pl.progressChanged(this, 20);
    if (job.contains3dRaster()) {
      out.write(this.generatePseudoRaster3dGCode(job.getRaster3dPart(), job.getResolution()));
    }
    pl.progressChanged(this, 40);
    if (job.containsRaster()) {
      out.write(this.generatePseudoRasterGCode(job.getRasterPart(), job.getResolution()));
    }
    pl.progressChanged(this, 60);
    if (job.containsVector()) {
      out.write(this.generateVectorGCode(job.getVectorPart(), job.getResolution()));
    }
    pl.progressChanged(this, 80);
    out.write(this.generateShutdownCode());
    out.close();
    System.out.write(buffer.toByteArray());
    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
  }
  private List<Integer> resolutions;

  @Override
  public List<Integer> getResolutions() {
    if (resolutions == null) {
      resolutions = Arrays.asList(new Integer[]{
                500
              });
    }
    return resolutions;
  }
  protected double bedWidth = 250;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth() {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth) {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 280;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight() {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight) {
    this.bedHeight = bedHeight;
  }
  private List<String> settingAttributes;

  @Override
  public List<String> getSettingAttributes() {
    if (settingAttributes == null) {
      settingAttributes = new LinkedList<String>();
      settingAttributes.add(SETTING_BEDWIDTH);
      settingAttributes.add(SETTING_BEDHEIGHT);
      settingAttributes.add(SETTING_FLIPX);
      settingAttributes.add(SETTING_COMPORT);
      settingAttributes.add(SETTING_LASER_RATE);
      settingAttributes.add(SETTING_SEEK_RATE);
      settingAttributes.add(SETTING_RASTER_WHITESPACE);
    }
    return settingAttributes;
  }

  @Override
  public String getSettingValue(String attribute) {
    if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      return "" + this.getAddSpacePerRasterLine();
    } else if (SETTING_COMPORT.equals(attribute)) {
      return this.getComPort();
    } else if (SETTING_FLIPX.equals(attribute)) {
      return this.isFlipXaxis() ? "yes" : "no";
    } else if (SETTING_LASER_RATE.equals(attribute)) {
      return "" + this.getLaserRate();
    } else if (SETTING_SEEK_RATE.equals(attribute)) {
      return "" + this.getSeekRate();
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return "" + this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return "" + this.getBedHeight();
    }
    return null;
  }

  @Override
  public void setSettingValue(String attribute, String value) {
    if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      this.setAddSpacePerRasterLine(Double.parseDouble(value));
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComPort(value);
    } else if (SETTING_LASER_RATE.equals(attribute)) {
      this.setLaserRate(Double.parseDouble(value));
    } else if (SETTING_SEEK_RATE.equals(attribute)) {
      this.setSeekRate(Double.parseDouble(value));
    } else if (SETTING_FLIPX.equals(attribute)) {
      this.setFlipXaxis("yes".equals(value));
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth(Double.parseDouble(value));
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight(Double.parseDouble(value));
    }
  }

  @Override
  public int estimateJobDuration(LaserJob job) {
    return 10000;
  }

  @Override
  public LaserCutter clone() {
    Lasersaur clone = new Lasersaur();
    clone.comPort = comPort;
    clone.laserRate = laserRate;
    clone.seekRate = seekRate;
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    clone.flipXaxis = flipXaxis;
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    return clone;
  }
}
