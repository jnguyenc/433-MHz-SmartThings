def getGatewayAddr(){
	// SmartThing does not support global variables.  Use this special Groovy method to set up one
	// Called by gatewayAddr - no *get*, no *()*, captialized 1st letter after *get* in the method name becomes lowercased
    // set IP:port appropriately
	return "192.168.1.28:8083"
}

//	===========================================================
metadata {
	definition (name: "433 MHz SmartThing Device",
				namespace: "jcnguyen",
				author: "John Nguyen",
				energyMonitor: "Standard") {
		capability "Switch"
		capability "refresh"
		capability "polling"
		capability "Sensor"
		capability "Actuator"
	}
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "turningOff"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "turningOff"
				attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
            }
 			tileAttribute ("deviceMsg", key: "SECONDARY_CONTROL") {
				attributeState "deviceMsg", label: '${currentValue}'
			}
		}
        
		standardTile("refresh", "capability.refresh", width: 2, height: 2,  decoration: "flat") {
			state "default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
		main("switch")
		details("switch", "refresh", "status")
	}

	def rates = [:]
	rates << ["1" : "Refresh every minutes (Not Recommended)"]
	rates << ["5" : "Refresh every 5 minutes"]
	rates << ["10" : "Refresh every 10 minutes"]
	rates << ["15" : "Refresh every 15 minutes"]
	rates << ["30" : "Refresh every 30 minutes (Recommended)"]

	preferences {
		input("deviceID", "text", title: "Device ID", required: true, displayDuringSetup: true)
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
    state.status = "off"
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
	log.info "Device is $state.status. Sending command setState-on"
	sendCmdtoGateway("setState-on")
}

def off() {
	log.info "Device is $state.status. Sending command setState-off"
	sendCmdtoGateway("setState-off")
}

def refresh(){
	log.info "Sending refresh..."
	if( state.status == "on"){
		on()
	}
	else{
		off()
	}
}

def getStatus(){
	log.info "Device is $state.status. Getting Gateway Device status"
	sendComtoGateway("getStatus")
}

private sendCmdtoGateway(gatewayCommand){
    def headers = [:] 
	headers.put("HOST", gatewayAddr)
	headers.put("gateway-command", gatewayCommand)
	headers.put("device-id", deviceID)
	
	log.info "Sending command to gateway: $gatewayCommand, $deviceID, $gatewayAddr"
    sendHubCommand(new physicalgraph.device.HubAction([
		headers: headers],
		device.deviceNetworkId,
		[callback: parseGatewayResponse]
	))
}

def parseGatewayResponse(response){
	def gatewayResponse = response.headers["gateway-response"]
	def gatewayCommand = response.headers["gateway-commmand"]
	def gatewayDeviceStatus = response.headers["gateway-device-status"]
	
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
    	case "setState-on":
    		if(gatewayDeviceStatus == "on"){
				log.info "Device Status on: OK"
				sendEvent(name: "switch", value: "on")
				sendEvent(name: "deviceMsg", value: "Device Turned On: OK")
				state.status = "on"
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
			}
        break
        case "setState-off":
    		if(gatewayDeviceStatus == "off"){
				log.info "Device Status off: OK"
				sendEvent(name: "switch", value: "off")
				sendEvent(name: "deviceMsg", value: "Device Turned Off: OK")
				state.status = "off"
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
			}
        break
        case "getStatus":
    		if(gatewayDeviceStatus == state.status){
				log.info "Device Status OK"
				sendEvent(name: "deviceMsg", value: "Device Status: OK")
			}
			else{
				log.error "Something is not sync: $gatewayCommand $gatewayDeviceStatus"
			}
        break
	}
	log.info "===================================="
}
