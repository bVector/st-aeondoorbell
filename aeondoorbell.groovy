/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Aeon Siren
 *
 *	Author: Thomas Howard
 *	Date: 2015-11-10
 *  Modified 2015-11-29 (bVector)
 */
 
preferences {
	input("ringTone", "number", title: "Ringtone (1-100)", required: true, defaultValue: 1, description: "Ringtone to Use for the Doorbell")
	input("volume", "number", title: "Volume (1-10)", required: true, defaultValue: 1, description: "Volume of the Door Bell")
	input("numTimes", "number", title: "Number (1-100)", required: true, defaultValue: 2, description: "Number of Times to Play the Ringtone")
}

metadata {
 definition (name: "Aeon Doorbell", namespace: "smartthings", author: "thoward1234") {
	capability "Alarm"
	capability "Switch"
    capability "Battery"
    capability "Switch Level"
    capability "Refresh"
    
	command "test"
    command "configure" 
    command "setRingTone"
    command "setNumRings" 
    command "setMute"
     
	fingerprint deviceId: "0x1005", inClusters: "0x5E,0x98"
 }

 simulator {
	// reply messages
	reply "9881002001FF,9881002002": "command: 9881, payload: 002003FF"
	reply "988100200100,9881002002": "command: 9881, payload: 00200300"
	reply "9881002001FF,delay 3000,988100200100,9881002002": "command: 9881, payload: 00200300"
 }

 tiles(scale: 2) {
	multiAttributeTile(name:"alarm", type: "lighting", width: 6, height: 4){
		tileAttribute ("device.alarm", key: "PRIMARY_CONTROL") {
			attributeState "off", label:'off', action:'alarm.siren', icon:"st.Electronics.electronics14", backgroundColor:"#ffffff"
			attributeState "both", label:'Activated', action:'alarm.off', icon:"st.Electronics.electronics14", backgroundColor:"#e86d13"
		}
        tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
		}
      
	}
    standardTile("ringtone", "device.ringTone", inactiveLabel: false, width: 2, height: 2) {
		state "default", label:'${currentValue}', action:"setRingTone", icon:"st.Electronics.electronics10"
	}
    standardTile("numRings", "device.numRings", inactiveLabel: false, width: 2, height: 2) {
		state "default", label:'${currentValue}', action:"setNumRings", icon:"st.Entertainment.entertainment2"
	}
    standardTile("volume", "device.volume", inactiveLabel: false, width: 2, height: 2) {
		state "default", label:'${currentValue}', action:"setMute", icon:"st.custom.sonos.unmuted"
        state "muted", label: 'Mute', action:"setMute", icon:"st.custom.sonos.unmuted"
	}
	standardTile("off", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", label:'', action:"alarm.off", icon:"st.secondary.off"
	}
    standardTile("configure", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", label:'Configure', action:"configure", icon:"st.Office.office6"
	}
   
	main "alarm"
	details(["alarm", "ringtone", "numRings", "volume", "off", "configure"])
 }
}

def updated() {
	try {
		Short ringTone = (settings.ringTone as Short) ?: 1
		Short volume = (settings.volume as Short) ?: 10
		Short numTimes = (settings.numTimes as Short) ?: 2
		response(updatePreferences(ringTone, volume, numTimes))
	} catch (e) {
		log.warn "updatePreferences failed: $e"
	}
}
def updatePreferences(Short ringTone, Short volume, Short numTImes){
	//Deal with lack of state data
	    
    log.debug "DOORBELL UPDATE:: Processing Selections: ringTone = $ringTone; volume = $volume; numTimes = $numTimes"
    delayBetween([
    			secure(zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId)),
              	secure(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)),
    			secure(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)),
        		secure(zwave.configurationV1.configurationSet(parameterNumber: 0x05, size: 1, scaledConfigurationValue: ringTone)),
                secure(zwave.configurationV1.configurationSet(parameterNumber: 0x08, size: 1, scaledConfigurationValue: volume)),
                secure(zwave.configurationV1.configurationSet(parameterNumber: 0x02, size: 1, scaledConfigurationValue: numTimes)),
    			secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
    ], 200);
}

def parse(String description) {
	log.debug "In parse callback ($description)"
	def result = null
	def cmd = zwave.parse(description, [0x98: 1, 0x20: 1, 0x70: 1, 0x80: 1, 0x25: 1])
	if (cmd) {
		result = zwaveEvent(cmd)
	}
	log.debug "Parse returned ${result?.inspect()}"
	return result
}

//HAIL COMMAND
def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	log.debug "Event: Hail ($cmd)"
}

//SECURITY ENCAPSULATED
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	log.debug "Event: Encapsulated: $cmd"
    def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x85: 2, 0x70: 1])
	if (encapsulatedCommand) {
    	log.debug"     $encapsulatedCommand"
		zwaveEvent(encapsulatedCommand)
	}
}

//BASIC REPORT
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	log.debug "Event: Basic ($cmd)"
	[
		createEvent([name: "switch", value: cmd.value ? "on" : "off", displayed: false]),
		createEvent([name: "alarm", value: cmd.value ? "both" : "off"])
	]
}

//SWITCH BINARY REPORT
def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	log.debug "Event: Binary Switch ($cmd)"
}

//COMMAND REPORT
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Event: Command ($cmd)"
	createEvent(displayed: false, descriptionText: "$device.displayName: $cmd")()
}

//MULTI-LEVEL
def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	log.debug "Event: MultiLevel ($cmd)"
}

//BASIC SET
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
    log.debug "Event: Basic Set ($cmd)"    
}

//BATTERY REPORT
def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "Event: Battery Report ($cmd)"    
}

//ASSOCIATION REPORT
def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
	log.debug "Event: Association Report ($cmd)"    
}

def setLevel(value) {
	def valueaux = value as Integer
	def level_ = Math.round(Math.min(valueaux, 99)/10)
	def request =  [
    		zwave.configurationV1.configurationSet(parameterNumber: 0x08, size: 1, scaledConfigurationValue: level_)
    ];
    log.debug "Selected Volume Level is $level_"
    setMuteVal(level_ as Short)
    if (level!=0) state.volume = level_;
    commands(request);
    
}

def setRingTone(){
    if(!state.ringTone) state.ringTone = 1
	log.debug "Current RingTone: $state.ringTone"
    state.ringTone = state.ringTone+1;
    if (state.ringTone > 5) state.ringTone = 1;
    log.debug "Setting RingTone: $state.ringTone"
    
    def request =  [
    		zwave.configurationV1.configurationSet(parameterNumber: 0x05, size: 1, scaledConfigurationValue: state.ringTone)
    ];
    def dispValue = String.format("#%d", state.ringTone);
    log.debug "Send Event $dispValue"
    sendEvent(name: "ringTone", value: dispValue as String, display: false);
    commands(request);
}

def setNumRings(){
    if(!state.numRings) state.numRings = 1
	log.debug "Current numRings: $state.numRings"
    state.numRings = state.numRings+1;
    if (state.numRings > 5) state.numRings = 1;
    log.debug "Setting numRings: $state.numRings"
    
    def request =  [
    		zwave.configurationV1.configurationSet(parameterNumber: 0x02, size: 1, scaledConfigurationValue: state.numRings)
    ];
    def dispValue = String.format("%d", state.numRings);
    sendEvent(name: "numRings", value: dispValue as String, display: false);
    commands(request);
}

def setMuteVal(Short level){
	
    if (level == 0) state.mute = true;
    else state.mute = false;
    log.debug "Mute = $state.mute"
    if (state.mute){ 
    	def dispValue = String.format("--");
    	sendEvent(name:"volume", value:dispValue as String, display:false)
    } else {
        def dispValue = String.format("%d", level);
    	sendEvent(name:"volume", value: dispValue as String, display:false);
     }
    
    
}

def setMute(){
	
    def level_ = 0;
   
    if (state.mute) {
        level_ = state.volume;
        state.mute = false;
    } else {
    	state.mute = true;
    }
    setMuteVal(level_ as Short);
    
    def request =  [
    		zwave.configurationV1.configurationSet(parameterNumber: 0x08, size: 1, scaledConfigurationValue: level_)
    ];
    log.debug "Selected Volume Level is $level_"
    commands(request);
    
}

def configure() {
	state.ringTone = 1;
	log.debug "Sending Configuration"
	delayBetween([
    	secure(zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId)),
    	secure(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)),
    	secure(zwave.configurationV1.configurationSet(parameterNumber: 0x80, size: 1, scaledConfigurationValue: 2)),  //Configure to send basic report
        secure(zwave.configurationV1.configurationSet(parameterNumber: 0x81, size: 1, scaledConfigurationValue: 2)),	  //Configure to send Battery Report
        secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
    ], 500)
    log.debug "Configuration Sent"
}

def refresh() {
	try {
		Short ringTone = (settings.ringTone as Short) ?: 1
		Short volume = (settings.volume as Short) ?: 10
		Short numTimes = (settings.numTimes as Short) ?: 2
		response(updatePreferences(ringTone, volume, numTimes))
	} catch (e) {
		log.warn "updatePreferences failed: $e"
	}	
}

def on() {
	log.debug "sending on"
	[
		secure(zwave.basicV1.basicSet(value: 0xFF)),
		secure(zwave.basicV1.basicGet())
	]
}

def off() {
	log.debug "sending off"
	[
		secure(zwave.basicV1.basicSet(value: 0x00)),
		secure(zwave.basicV1.basicGet())
	]
}

def strobe() {
	on()
}

def siren() {
	on()
}

def both() {
	on()
}

def test() {
	[
		secure(zwave.basicV1.basicSet(value: 0xFF)),
		"delay 3000",
		secure(zwave.basicV1.basicSet(value: 0x00)),
		secure(zwave.basicV1.basicGet())
	]
}

private secure(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private commands(commands, delay=200) {
	delayBetween(commands.collect{ secure(it) }, delay)
}
