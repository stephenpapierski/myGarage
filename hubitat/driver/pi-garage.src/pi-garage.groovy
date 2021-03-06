/**
 * PiGarage Smart Garage Door Controller
 *
 * Copyright 2020 Stephen Papierski
 *
 * Licensed under the GNU General Public License v3.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *     http://gnu.org/licenses/gpl-3.0.en.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Change History:
 *
 *   Date        Who                   What
 *   ----        ---                   ----
 *   2020-01-19  Stephen Papierski     Initial Commit
 *   2020-11-22  Stephen Papierski     Removing stoppedOpen to align with raspberry pi code update
 * 
 */
metadata {
    definition (name: "PiGarage Door Controller", namespace: "PinonValleyProjects", author: "Stephen Papierski", importUrl: "https://raw.githubusercontent.com/stephenpapierski/PiGarage/master/hubitat/driver/pi-garage.src/pi-garage.groovy") {
        capability "GarageDoorControl"
        capability "Refresh"
        //capability "Lock"     //Enable ability to keep the garage door shut
        //capability "Chime"    //Enable ability to sound chime before closing door

        attribute "lastActivity", "String"
        attribute "lastEvent", "String"
        attribute "responding", "Boolean"

        command "open"
        command "close"
    }

    preferences {
        input(name: "deviceIP", type: "string", title: "Device IP Address", description: "Enter IP Address of your HTTP server", required: true, displayDuringSetup: true)
        input(name: "devicePort", type: "string", title: "Device Port", description: "Enter Port of your HTTP server (defaults to 5000)", defaultValue: "5000", required: false, displayDuringSetup: true)
        input(name: "transitionTime", type: "number", title: "Transition Time", description: "Number of seconds it takes for door to transition from open to closed (round up).", defaultValue: "15", required: true, displayDuringSetup: true)
        input(name: "actuateDuration", type: "number", title: "Actuate Duration", description: "Number of milliseconds to actuate the relay to open or close the door.", defaultValue: "500", required: false, displayDuringSetup: true)
        input(name: "debugEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
    }
}

//HTTP POST requests to port 39501 from a device that matches the Device Network Id end up here
def parse(String description) {
    def msg = parseLanMessage(description)
    def body=msg.body
    body = parseJson(body)
    def status = body.status
    def isNew = body.isNew
    if (settings.debugEnable){
        if (debugEnable) log.debug("Status = $status")
    }
    sendEvent(name:"door", value:status, isStateChanged:isNew)
    
    //Record last activity
    recordActivity("garage reported ${status}")
    responding()
}

def updated(){
    def postData = ["transitionTime":settings.transitionTime,"actuateDuration":settings.actuateDuration]
    sendCmd(devicePath + "/configure/", postData)
    refresh()
    
}

def close() {
    recordActivity("hub sent close command")
    sendCmd(devicePath + "/close/", [:])
}

def open() {
    recordActivity("hub sent open command")
    sendCmd(devicePath + "/open/", [:])
}

def refresh() {
    recordActivity("hub sent refresh command")
    sendCmd(devicePath + "/refresh/", [:])
}

def sendCmd(String action, Map postData) {
    def localDevicePort = (devicePort==null) ? "5000" : devicePort 
    
    def url = "http://${deviceIP}:${localDevicePort}/${action}"
    
    try { 
        httpPostJson(url,postData) { resp -> 
            if (resp.isSuccess()){
                responding()
            } else {
                notResponding()
            }
        }
    }
    catch (Exception e) {
        if (debugEnable) log.debug "sendCmd hit exception ${e} on POST"
        notResponding()
    }
}

def notResponding(){
    sendEvent(name:"responding", value:false)
    sendEvent(name:"door", value:"unknown", isStateChanged:isNew)
}
def responding(){
    sendEvent(name:"responding", value:true)
}
def recordActivity(String event){
    //Record last activity
    def now
    if(location.timeZone)
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    else
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a")
    sendEvent(name: "lastActivity", value: now, isStateChanged:isNew)
    sendEvent(name: "lastEvent", value: event, isStateChanged:isNew)
}
