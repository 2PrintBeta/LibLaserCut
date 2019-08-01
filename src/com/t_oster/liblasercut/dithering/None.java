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
package com.t_oster.liblasercut.dithering;

import com.t_oster.liblasercut.BlackWhiteRaster;
import com.t_oster.liblasercut.GreyscaleRaster;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class None extends DitheringAlgorithm
{

  @Override
  protected void doDithering(GreyscaleRaster src, BlackWhiteRaster target)
  {
    
    for (int x = 0; x < src.getWidth(); x++)
    {
      for (int y = 0; y < src.getHeight(); y++)
      {
        this.setBlack(src, target, x, y, src.getGreyScale(x, y) <= 127);
      }
      
      setProgress((100 * x) / (src.getHeight()));
    }
    
  }

  @Override
  public DitheringAlgorithm clone() {
    return new None();
  }

  @Override
  public String toString()
  {
    return "None";
  }
}
