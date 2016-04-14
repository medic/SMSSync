/*****************************************************************************
 ** Copyright (c) 2010 - 2012 Ushahidi Inc
 ** All rights reserved
 ** Contact: team@ushahidi.com
 ** Website: http://www.ushahidi.com
 **
 ** GNU Lesser General Public License Usage
 ** This file may be used under the terms of the GNU Lesser
 ** General Public License version 3 as published by the Free Software
 ** Foundation and appearing in the file LICENSE.LGPL included in the
 ** packaging of this file. Please review the following information to
 ** ensure the GNU Lesser General Public License version 3 requirements
 ** will be met: http://www.gnu.org/licenses/lgpl.html.
 **
 **
 ** If you have questions regarding the use of this file, please contact
 ** Ushahidi developers at team@ushahidi.com.
 **
 *****************************************************************************/
package org.addhen.smssync.util;

import android.util.Xml;

import org.apache.http.NameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * Class: DataFormatUtil
 * Description: Serialize sync data in appropriate format.
 * Author: Salama A.B. <devaksal@gmail.com>
 */
public class DataFormatUtil {
    public static String makeJSONString(List<NameValuePair> pairs) throws JSONException{
        JSONObject obj = new JSONObject();

        for(NameValuePair pair:pairs){
            obj.put(pair.getName(),pair.getValue());
        }

        return obj.toString();
    }
}
