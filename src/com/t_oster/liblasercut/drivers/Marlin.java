/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 **/
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.Raster3dPart;
import com.t_oster.liblasercut.RasterPart;
import com.t_oster.liblasercut.RasterizableJobPart;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.drivers.GenericGcodeDriver;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Util;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Base64;
/**
 * This class implements a driver for the laser cutter fork of Marlin.
 * 
 * @author quillford
 */
public class Marlin extends GenericGcodeDriver {

  protected static final String SETTING_RASTER_G7 = "Use G7 rastering";
  protected static final String SETTING_OPTIMISE_RASTER = "Optimise rastering";
  protected static final String SETTING_SANATISE_PRONTERFACE = "Sanatise for pronterface";

  
  public Marlin()
  {
    //set some Marlin specific defaults
    setIdentificationLine("start");
    setWaitForOKafterEachLine(true);
    setBaudRate(115200);
    setLineend("CRLF");
    setInitDelay(0);
    setPreJobGcode(getPreJobGcode()+",G28 XY,M5");
    setPostJobGcode(getPostJobGcode()+",M5,G28 XY");
    setSerialTimeout(35000);
    setBlankLaserDuringRapids(false);
    setSpindleMax(100.0); // marlin interprets power from 0-100 instead of 0-1
    
    //Marlin has no way to upload over the network so remove the upload url text
    setHttpUploadUrl("");
    setHost("");
  }
  
  /**
   * Adjust defaults after deserializing driver from an old version of XML file
   */
  @Override
  protected void setKeysMissingFromDeserialization()
  {
    // added field spindleMax, needs to be set to 100.0 for Marlin
    // but xstream initializes it to 0.0 when it is missing from XML
    if (this.spindleMax <= 0.0) this.spindleMax = 100.0;
  }
  
  @Override
  public String getIdentificationLine()
  {
    return("start");
  }

  @Override
  public String[] getPropertyKeys()
  {
    List<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.remove(GenericGcodeDriver.SETTING_IDENTIFICATION_STRING);
    result.remove(GenericGcodeDriver.SETTING_WAIT_FOR_OK);
    result.remove(GenericGcodeDriver.SETTING_LINEEND);
    result.remove(GenericGcodeDriver.SETTING_INIT_DELAY);
    result.remove(GenericGcodeDriver.SETTING_HTTP_UPLOAD_URL);
    result.remove(GenericGcodeDriver.SETTING_HOST);
    result.remove(GenericGcodeDriver.SETTING_SPINDLE_MAX);
    result.remove(GenericGcodeDriver.SETTING_BLANK_LASER_DURING_RAPIDS);
    result.add(SETTING_RASTER_G7);
    result.add(SETTING_OPTIMISE_RASTER); 
    return result.toArray(new String[0]);
  }
  
  @Override
  public Object getProperty(String attribute) {
    
    if (SETTING_RASTER_G7.equals(attribute)) {
      return this.getG7rastering();
    }
    if (SETTING_OPTIMISE_RASTER.equals(attribute)) {
      return this.getOptimiseRastering();
    }
    
    return super.getProperty(attribute);
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_RASTER_G7.equals(attribute)) {
      this.setG7rastering((Boolean) value);
    }
    if (SETTING_OPTIMISE_RASTER.equals(attribute)) {
      this.setOptimiseRastering((Boolean) value);
    }
    super.setProperty(attribute, value);
  }

  
  /**
   * Waits for the Identification line and returns null if it's alright
   * Otherwise it returns the wrong line
   * @return
   * @throws IOException 
   */
 @Override
  protected String waitForIdentificationLine(ProgressListener pl) throws IOException
  {
    if (getIdentificationLine() != null && getIdentificationLine().length() > 0)
    {
      String line = waitForLine();
        if (line.startsWith(getIdentificationLine()))
        {//we received the identification line ("start"), now we have to skip the rest of Marlin's dump
          while(!(waitForLine().startsWith("echo:SD")))
          {
           //do nothing and wait until Marlin has dumped all of the settings
          }
          return null;
        }
    }
    return null;
  }

  protected boolean useG7rastering = false;

  public boolean getG7rastering()
  {
    return useG7rastering;
  }

  public void setG7rastering(boolean useG7rastering)
  {
    this.useG7rastering = useG7rastering;
  }
  
  protected boolean optimiseRastering = false;

  public boolean getOptimiseRastering()
  {
    return optimiseRastering;
  }

  public void setOptimiseRastering(boolean setting)
  {
    this.optimiseRastering = setting;
  }
  
  @Override
  public String getModelName()
  {
    return "Marlin";
  }

  @Override
  public Marlin clone()
  {
    Marlin clone = new Marlin();
    clone.copyProperties(this);
    return clone;
  }
  
  
  private byte[][] splitBytes(final byte[] data, final int chunkSize)
  {
    final int length = data.length;
    final byte[][] dest = new byte[(length + chunkSize - 1)/chunkSize][];
    int destIndex = 0;
    int stopIndex = 0;

    for (int startIndex = 0; startIndex + chunkSize <= length; startIndex += chunkSize)
    {
      stopIndex += chunkSize;
      dest[destIndex++] = Arrays.copyOfRange(data, startIndex, stopIndex);
    }

    if (stopIndex < length)
      dest[destIndex] = Arrays.copyOfRange(data, stopIndex, length);

    return dest;
  }
  
  protected void writeRasterGCode(RasterizableJobPart rp, double resolution, boolean bidirectional) throws UnsupportedEncodingException, IOException {
     
    //setup laser config for this part
    double pixel_size = 1/(resolution / 25.4);
    sendLine(";Beginning of Raster Image. Image size: %dx%d",rp.getRasterWidth(),rp.getRasterHeight());
    sendLine("M649 S%f B2 D0 R%f",rp.getPowerSpeedFocusPropertyForColor(0).getPower(),pixel_size);
    currentPower = rp.getPowerSpeedFocusPropertyForColor(0).getPower();
      
    //prepare overscan array
    int overscan = Math.round((float)Util.mm2px(this.getRasterPadding(), resolution));
    ArrayList<Byte> overscanArray = new ArrayList<Byte>();
    for (int i=0; i<overscan; i++){
      overscanArray.add((byte) rp.getPowerSpeedFocusPropertyForColor(255).getPower());
    }
    
    //iterate over all raster lines
    boolean forward = true;
    for (int y = rp.getRasterHeight()-1; y >= 0; y--)
    {
       //skip line if it is empty
       if(rp.lineIsBlank(y)) continue;
       
       //find start and end of line
       int start = rp.firstNonWhitePixel(y);
       int end = rp.lastNonWhitePixel(y);
       Point lineStart = rp.getStartPosition(y);
       
       // go to start position
       if(forward)  move(out,lineStart.x + start - overscan,lineStart.y,resolution);
       else  move(out,lineStart.x + start+ overscan,lineStart.y,resolution);
      
       //set speed
       sendLine("G0 F%f",(rp.getPowerSpeedFocusPropertyForColor(0).getSpeed()*max_speed)/100);
  
       //prepare data for line
       ArrayList<Byte> data = new ArrayList<Byte>();
        
       if(forward)
       {
          for(int x=start; x<end;x++)
          {
             data.add((byte) Math.round(rp.getPowerSpeedFocusPropertyForPixel(x, y).getPower()/rp.getPowerSpeedFocusPropertyForColor(0).getPower()*254) );
          }
       }
       else
       {
          for(int x=start-1;x>=end;x--)
          {
             data.add((byte) Math.round(rp.getPowerSpeedFocusPropertyForPixel(x, y).getPower()/rp.getPowerSpeedFocusPropertyForColor(0).getPower()*254) );
          }
       }
        
       //prepend and append overscan array
       data.addAll(0, overscanArray);
       data.addAll(overscanArray);    

       //split the data in chunks
       byte[] dataArray = new byte[data.size()];
       for(int i = 0; i < data.size(); i++) {
           dataArray[i] = data.get(i);
       }
       byte chunks[][] = splitBytes(dataArray,51);
        
       //for chunk in get_chunks(result_row,51):
       boolean first = true;
       for (byte[] chunk : chunks)
       {
         String gcode = "";
         if (first)
         {
            if(forward) gcode += ("G7 $1 ");
            else gcode += ("G7 $0 ");
            first = !first;
         }
         else
            gcode +=  ("G7 ");
         
         //encode the whole chunk as base64
         String b64 = new String(Base64.getEncoder().encode(chunk));
         //String b64 = base64.b64encode("".join(chr(y) for y in chunk))
         gcode += ("L"+Integer.toString(b64.length())+" ");
         gcode += ("D"+b64);
         sendLine(gcode);
              
       }
       
       //toggle direction if wanted
       if( this.useBidirectionalRastering)
       {
        rp.toggleRasteringCutDirection();
        forward = !forward;
       }
    }
    
     sendLine("M5");
     sendLine("M649 S%f B0 D0 R%f",rp.getPowerSpeedFocusPropertyForColor(0).getPower(),1/(resolution / 25.4));
     sendLine(";End of Raster Image.");
     sendLine("G28 XY");

  }
   
  
  
 @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;

    pl.taskChanged(this, "checking job");
    checkJob(job);
    this.jobName = job.getName()+".gcode";
    job.applyStartPoint();
    pl.taskChanged(this, "connecting...");
    connect(pl);
    pl.taskChanged(this, "sending");
    try {
      writeInitializationCode();
      pl.progressChanged(this, 20);
      int i = 0;
      int max = job.getParts().size();
      for (JobPart p : job.getParts())
      {
        if (p instanceof Raster3dPart || p instanceof RasterPart)
        {
          if(this.useG7rastering)
          {
            // G7 rastering
            writeRasterGCode((RasterizableJobPart) p, p.getDPI(), getUseBidirectionalRastering());
          }
          else
          {
            p = convertRasterizableToVectorPart((RasterizableJobPart) p, p.getDPI(), getUseBidirectionalRastering());
          }
        }
        if (p instanceof VectorPart)
        {
          //TODO: in direct mode use progress listener to indicate progress
          //of individual job
          writeVectorGCode((VectorPart) p, p.getDPI());
        }
        i++;
        pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
      }
      writeShutdownCode();
      disconnect(job.getName()+".gcode");
    }
    catch (IOException e) {
      pl.taskChanged(this, "disconnecting");
      disconnect(this.jobName);
      throw e;
    }
    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
  }
  @Override
  public void saveJob(java.io.PrintStream fileOutputStream, LaserJob job,ProgressListener pl) throws IllegalJobException, Exception {
    this.currentPower = -1;
    this.currentSpeed = -1;

    checkJob(job);

    this.out = fileOutputStream;

    boolean wasSetWaitingForOk = isWaitForOKafterEachLine();
    setWaitForOKafterEachLine( false );

    writeInitializationCode();
    for (JobPart p : job.getParts())
    {
      if (p instanceof Raster3dPart || p instanceof RasterPart)
      {
          if(this.useG7rastering)
          {
            //G7 rastering
            writeRasterGCode((RasterizableJobPart) p, p.getDPI(), getUseBidirectionalRastering());
          }
          else
          {
            p = convertRasterizableToVectorPart((RasterizableJobPart) p, p.getDPI(), getUseBidirectionalRastering());
          }
      }
      if (p instanceof VectorPart)
      {
        writeVectorGCode((VectorPart) p, p.getDPI());
      }
    }
    writeShutdownCode();
    this.out.flush();

    setWaitForOKafterEachLine(wasSetWaitingForOk);
  }
}