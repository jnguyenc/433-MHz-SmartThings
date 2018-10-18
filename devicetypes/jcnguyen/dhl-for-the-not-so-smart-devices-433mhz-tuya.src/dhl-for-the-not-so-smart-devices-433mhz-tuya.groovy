def getGatewayAddr(){
	// SmartThing does not support global variables.  Use this special Groovy method to set up one
	// Called by gatewayAddr - no *get*, no *()*, captialized 1st letter after *get* in the method name becomes lowercased
	// set IP:port appropriately
	return "192.168.1.28:8082"
}

//	===========================================================
metadata {
	definition (name: "DHL for the Not So Smart Devices (433mhz, Tuya)",
				namespace: "jcnguyen",
				author: "John Nguyen",
				energyMonitor: "Standard") {
		capability "Switch"
		capability "refresh"
		capability "polling"
		capability "Sensor"
		capability "Actuator"
        
        command "syncFromGateway"
	}
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "turningOff"
				attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#91c6da", nextState: "turningOn"
            }
 			tileAttribute ("deviceMsg", key: "SECONDARY_CONTROL") {
				attributeState "deviceMsg", label: '${currentValue}'
			}
		}
        
		standardTile("refresh", "capability.refresh", width: 2, height: 2,  decoration: "flat") {
			state "default", label:"Refresh Gateway", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		
		standardTile("syncFromGateway", "device.syncFromGateway", width: 2, height: 2,  decoration: "flat") {
			state "default", label:"Sync from Gateway", action:"syncFromGateway", icon:"st.Kids.kids8"
		}
        
		main("switch")
		details("switch", "refresh", "syncFromGateway")
	}

	def rates = [:]
	rates << ["1" : "Refresh every minutes (Not Recommended)"]
	rates << ["5" : "Refresh every 5 minutes"]
	rates << ["10" : "Refresh every 10 minutes"]
	rates << ["15" : "Refresh every 15 minutes"]
	rates << ["30" : "Refresh every 30 minutes (Recommended)"]

	preferences {
		input("deviceID", "text", title: "Device ID", required: true, displayDuringSetup: true)
		input("gatewayAddress", "text", title: "Gateway Address", defaultValue: "192.168.1.28:8083", required: false, displayDuringSetup: true)
		input name: "refreshRate", type: "enum", title: "Refresh Rate", options: rates, description: "Select Refresh Rate", required: false
	}
}

//	===== Update when installed or setting changed =====
def installed() {
	update()
}

def updated() {
	runIn(2, update)
}

def update() {
	unschedule()
	switch(refreshRate) {
		case "1":
			runEvery1Minute(refresh)
			log.info "Refresh Scheduled for every minute"
			break
		case "5":
			runEvery5Minutes(refresh)
			log.info "Refresh Scheduled for every 5 minutes"
			break
		case "10":
			runEvery10Minutes(refresh)
			log.info "Refresh Scheduled for every 10 minutes"
			break
		case "15":
			runEvery15Minutes(refresh)
			log.info "Refresh Scheduled for every 15 minutes"
			break
		default:
			runEvery30Minutes(refresh)
			log.info "Refresh Scheduled for every 30 minutes"
	}
	runIn(5, refresh)
}

void uninstalled() {
}

//	===== Basic Plug Control/Status =====
def on() {
	log.info "Device is ${device.currentValue("switch")}. Sending command state-on"
	sendCmdtoGateway("state-on")
}

def off() {
	log.info "Device is ${device.currentValue("switch")}. Sending command state-off"
	sendCmdtoGateway("state-off")
}

def refresh(){
	log.info "Device is ${device.currentValue("switch")}. Sending refresh command."
	if( device.currentValue("switch") == "on"){
		on()
	}
	else{
		off()
	}
}

def syncFromGateway(){
	log.info "Device is ${device.currentValue("switch")}. Getting Gateway Device status"
	sendCmdtoGateway("status")
}

private sendCmdtoGateway(command){
    def headers = [:]
	def ip_port = gatewayAddr
	// user entered gateway address overrides global default value
    if (gatewayAddress){
		ip_port = gatewayAddress
	}
	headers.put("HOST", ip_port)
	//headers.put("command", command)
	//headers.put("dev_id", deviceID)//header name is forced to lowercase, so don't use uppercase letters
    //compile data into one header
    def request_data = '{"dev_id":"'+deviceID+'","command":"'+command+'"}'
    headers.put("request_data", request_data)
    
	/*================================
    log.info "data: $request_data"
    def slurper = new groovy.json.JsonSlurper()
    def result = slurper.parseText(request_data)
 	log.info "Result: $result.dev_id"
    =====================================*/
        
	log.info "Sending command to gateway: $command, $deviceID, $ip_port"
    sendHubCommand(new physicalgraph.device.HubAction(
    	[headers: headers],
		device.deviceNetworkId,
		[callback: parseGatewayResponse]
	))
}

def parseGatewayResponse(response){
	//def gatewayResponse = response.headers["command_response"]
	//def gatewayCommand = response.headers["command"]
	//def gatewayDeviceStatus = response.headers["dev_status"]
	def response_data = response.headers["response_data"]
	
	/*=============================================
	def slurper = new groovy.json.JsonSlurper()
 	def result = slurper.parseText('{"person":{"name":"Guillaume","age":33,"pets":["dog","cat"]}}')
 	log.info "Result $result.person.name"
    result.person.name = "John Nguyen"
    log.info "Result $result.person.name"
	==============================================*/
	
    def slurper = new groovy.json.JsonSlurper()
 	def result = slurper.parseText(response_data)
 	
    def gatewayResponse = result.command_response
	def gatewayCommand = result.command
    
    def gatewayDeviceStatus = "off"
    if(result.dev_state){
		gatewayDeviceStatus = "on"
	}
    log.info "Device state is $gatewayDeviceStatus"
    
	switch(gatewayResponse){
		case "OK":
			log.info "Gateway command processed successfully: $gatewayCommand"
			parsegatewayDeviceStatus(gatewayCommand, gatewayDeviceStatus)
			break
		case "InvalidGatewayCommand":
			log.error "Invalid gateway command: $gatewayCommand"
			break
		case "GatewayError":
			log.error "Error on gateway"
			break
		default:
			log.error "Unable to understand gateway response"
	}
}
private parsegatewayDeviceStatus(gatewayCommand, gatewayDeviceStatus){
    switch(gatewayCommand){
    	case "state-on":
    		if(gatewayDeviceStatus == "on"){
				log.info "Device Status on: OK"
				sendEvent(name: "switch", value: "on")
				sendEvent(name: "deviceMsg", value: "Device Turned On: OK")
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
			}
        break
        case "state-off":
    		if(gatewayDeviceStatus == "off"){
				log.info "Device Status off: OK"
				sendEvent(name: "switch", value: "off")
				sendEvent(name: "deviceMsg", value: "Device Turned Off: OK")
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
			}
        break
        case "status":
    		if(gatewayDeviceStatus == device.currentValue("switch")){
				log.info "Device Status Synced"
				sendEvent(name: "deviceMsg", value: "Device Status: Synced")
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
                log.info "Syncing from Gateway..."
                sendEvent(name: "switch", value: "$gatewayDeviceStatus")
				sendEvent(name: "deviceMsg", value: "Synced from Gateway: ${gatewayDeviceStatus.toUpperCase()}")
                log.info "Synced $gatewayDeviceStatus from Gateway"
			}
        break
	}
	log.info "===================================="
}
