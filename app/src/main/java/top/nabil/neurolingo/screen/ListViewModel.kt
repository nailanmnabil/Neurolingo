package top.nabil.neurolingo.screen

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurosky.AlgoSdk.NskAlgoDataType
import com.neurosky.AlgoSdk.NskAlgoSdk
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoDataStream
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoInit
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoStart
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoStop
import com.neurosky.AlgoSdk.NskAlgoSdk.OnBPAlgoIndexListener
import com.neurosky.AlgoSdk.NskAlgoType
import com.neurosky.connection.ConnectionStates
import com.neurosky.connection.DataType.MindDataType
import com.neurosky.connection.EEGPower
import com.neurosky.connection.TgStreamHandler
import com.neurosky.connection.TgStreamReader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.nabil.neurolingo.bluetooth.BluetoothController
import top.nabil.neurolingo.bluetooth.BluetoothDeviceDomain

private const val TAG = "tg-callback"

data class BluetoothUiState(
    val pairedBluetooth: List<BluetoothDeviceDomain> = emptyList(),
    val scannedBluetooth: List<BluetoothDeviceDomain> = emptyList(),

    val state: String = "",
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isDuringSession: Boolean = false,
    val selectedAddress: String = "",

    val rawData: Int = 0,
    val delta: Int = 0,
    val theta: Int = 0,
    val lowAlpha: Int = 0,
    val highAlpha: Int = 0,
    val lowBeta: Int = 0,
    val highBeta: Int = 0,
    val lowGamma: Int = 0,
    val middleGamma: Int = 0,

    val xS: Float = 0f,
    val yS: Float = 0f,
    val xE: Float = 0f,
    val rawGraph: MutableList<List<Float>> = mutableListOf(),
)

sealed class ListScreenEvent {
    data class ShowToast(val message: String) : ListScreenEvent()
}

class ListViewModel(
    private val bluetoothController: BluetoothController,
    private val recordedFilePath: String
) : ViewModel() {

    // TG related var
    private var tgStreamReader: TgStreamReader? = null
    private var badPacketCount = 0
    private val MSG_UPDATE_BAD_PACKET = 1001
    private val MSG_UPDATE_STATE = 1002

    // EEG Algo related var
    private val rawData = shortArrayOf(0)
    private val rawDataIndex = 0
//    private val nskAlgoSdk: NskAlgoSdk = NskAlgoSdk()

    private val _state = MutableStateFlow(BluetoothUiState())
    val state = combine(
        bluetoothController.pairedDevice,
        bluetoothController.scannedDevice,
        bluetoothController.isScanning,
        _state
    ) { pairedDevice, scannedDevice, isScanning, state ->
        state.copy(
            pairedBluetooth = pairedDevice,
            scannedBluetooth = scannedDevice,
            isScanning = isScanning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private val _eventFlow = MutableSharedFlow<ListScreenEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val algoIndexListener: OnBPAlgoIndexListener =
        OnBPAlgoIndexListener { delta, theta, alpha, beta, gamma ->
            Log.i(
                TAG, "NskAlgoBPAlgoIndexListener: BP: D[" + delta + " dB] T[" + theta + " dB] A[" +
                        alpha + " dB] B[" + beta + " dB] G[" + gamma + "]"
            );
        }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    fun startAnalyze(context: Context) {
        _state.update { it.copy(isConnecting = false) }
//        NskAlgoInit(NskAlgoType.NSK_ALGO_TYPE_BP.value, context.filesDir.absolutePath)
//        nskAlgoSdk.setOnBPAlgoIndexListener(algoIndexListener)
//        NskAlgoStart(false)
    }

    fun startRecord() {
        _state.update {
            it.copy(
                isDuringSession = true,
                isConnected = false
            )
        }
    }

    fun stopRecord() {
        _state.update {
            it.copy(isDuringSession = false)
        }
        tgStreamReader?.stopRecordRawData()
        tgStreamReader?.stop()
        tgStreamReader?.close()
    }

    fun resetRawGraph() {
        _state.update {
            it.copy(
                rawGraph = mutableListOf(),
                xS = 0f,
                yS = 0f,
                xE = 0f
            )
        }
    }

    fun connect(address: String) {
        bluetoothController.stopDiscovery()

        val bd = bluetoothController.bluetoothAdapter?.getRemoteDevice(address)

        if (tgStreamReader == null) {
            tgStreamReader = TgStreamReader(bd, callback)
            tgStreamReader?.startLog()
            tgStreamReader?.setGetDataTimeOutTime(100)
            tgStreamReader?.setRecordStreamFilePath(recordedFilePath)
        } else {
            tgStreamReader?.changeBluetoothDevice(bd)
            tgStreamReader?.setTgStreamHandler(callback)
            tgStreamReader?.startLog()
            tgStreamReader?.setGetDataTimeOutTime(100)
            tgStreamReader?.setRecordStreamFilePath(recordedFilePath)
        }

        _state.update {
            it.copy(
                isConnecting = true,
                selectedAddress = address,
                state = "menyambungkan..."
            )
        }
        tgStreamReader?.connectAndStart()
    }

    private val callback: TgStreamHandler = object : TgStreamHandler {
        override fun onStatesChanged(connectionStates: Int) {
            Log.d(TAG, "STATE: $connectionStates")

            when (connectionStates) {
                ConnectionStates.STATE_CONNECTING -> {
                    Log.d(TAG, "Connecting")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Connecting"))
//                    }
                }

                ConnectionStates.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected")

                    _state.update {
                        it.copy(state = "tersambung...")
                    }

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Connected"))
//                    }
                }

                ConnectionStates.STATE_WORKING -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            state = "bisa dimulai"
                        )
                    }
                    tgStreamReader?.startRecordRawData()

                    Log.d(TAG, "Working")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Working"))
//                    }
                }

                ConnectionStates.STATE_GET_DATA_TIME_OUT -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            state = "gagal tersambung"
                        )
                    }

                    tgStreamReader?.stopRecordRawData()

                    Log.d(TAG, "Stopped")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Stopped"))
//                    }
                }

                ConnectionStates.STATE_FAILED -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isDuringSession = false,
                            isConnected = false,
                            state = "gagal tersambung"
                        )
                    }
                }
            }

            val msg: Message = linkDetectedHandler.obtainMessage()
            msg.what = MSG_UPDATE_STATE
            msg.arg1 = connectionStates
            linkDetectedHandler.sendMessage(msg)
        }

        override fun onRecordFail(a: Int) {
            Log.e(TAG, "onRecordFail: $a")
        }

        override fun onChecksumFail(payload: ByteArray, length: Int, checksum: Int) {
            Log.e(TAG, "onChecksumFail: $length")

            badPacketCount++
            val msg: Message = linkDetectedHandler.obtainMessage()
            msg.what = MSG_UPDATE_BAD_PACKET
            msg.arg1 = badPacketCount
            linkDetectedHandler.sendMessage(msg)
        }

        override fun onDataReceived(datatype: Int, data: Int, obj: Any?) {
            Log.e(TAG, "onDataReceived: $data")

            val msg = linkDetectedHandler.obtainMessage()
            msg.what = datatype
            msg.arg1 = data
            msg.obj = obj
            linkDetectedHandler.sendMessage(msg)
        }
    }

    @SuppressLint("HandlerLeak")
    private val linkDetectedHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MindDataType.CODE_RAW -> {
                    Log.d(TAG, "CODE_RAW " + msg.arg1)

                    val data = msg.arg1.toFloat()

                    _state.value.rawGraph.add(
                        listOf(
                            _state.value.xS,
                            _state.value.yS,
                            _state.value.xE,
                            data
                        )
                    )

                    _state.update {
                        it.copy(
                            rawData = msg.arg1,
                            xS = it.xE,
                            yS = data,
                            xE = it.xS + 1.dp.value
                        )
                    }

//                    NskAlgoDataStream(
//                        NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value,
//                        msg.arg1,
//                        raw_data_index
//                    )
                }

                MindDataType.CODE_MEDITATION -> {
                    Log.d(TAG, "CODE_MEDITATION " + msg.arg1)
                }

                MindDataType.CODE_ATTENTION -> {
                    Log.d(TAG, "CODE_ATTENTION " + msg.arg1)
                }

                MindDataType.CODE_POOR_SIGNAL -> {
                    val poorSignal = msg.arg1
                    Log.d(TAG, "CODE_POOR_SIGNAL $poorSignal")
                }

                MindDataType.CODE_EEGPOWER -> {
                    val power: EEGPower = msg.obj as EEGPower
                    Log.d(TAG, "MSG_UPDATE_BAD_PACKET " + msg.arg1)

                    if (power.isValidate) {
                        Log.d(TAG, "POWER.DELTA " + power.delta)
                        Log.d(TAG, "POWER.THETA " + power.theta)
                        Log.d(TAG, "POWER.LOW_ALPHA " + power.lowAlpha)
                        Log.d(TAG, "POWER.HIGH_ALPHA " + power.highAlpha)
                        Log.d(TAG, "POWER.LOW_BETA " + power.lowBeta)
                        Log.d(TAG, "POWER.HIGH_BETA " + power.highBeta)
                        Log.d(TAG, "POWER.LOW_GAMMA " + power.lowGamma)
                        Log.d(TAG, "POWER.MIDDLE_GAMMA " + power.middleGamma)

                        _state.update {
                            it.copy(
                                delta = power.delta,
                                theta = power.theta,
                                lowAlpha = power.lowAlpha,
                                highAlpha = power.highAlpha,
                                lowBeta = power.lowBeta,
                                highBeta = power.highBeta,
                                lowGamma = power.lowGamma,
                                middleGamma = power.middleGamma
                            )
                        }
                    }
                }

                MSG_UPDATE_BAD_PACKET -> {
                    Log.d(TAG, "MSG_UPDATE_BAD_PACKET ${msg.arg1}")
                }

                else -> {}
            }
            super.handleMessage(msg)
        }
    }
}
