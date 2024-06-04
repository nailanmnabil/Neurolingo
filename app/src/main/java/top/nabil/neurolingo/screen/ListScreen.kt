package top.nabil.neurolingo.screen

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.nabil.neurolingo.R
import top.nabil.neurolingo.ui.theme.Kanit

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: ListViewModel,
    context: Context
) {
    val state = vm.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = true) {
        vm.eventFlow.collect { event ->
            when (event) {
                is ListScreenEvent.ShowToast -> {
                    coroutineScope.launch {
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier
                    .background(Color(0xFFD9D9D9)),
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = null
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 64.dp
                ),
        ) {
            if (state.value.isDuringSession) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight(0.2f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.rawGraph) {
                            val yS = (l[1] - 1) / 19999 * size.height + 1
//                                if (l[1] > size.height) size.height else l[1]
                            val yE = (l[3] - 1) / 19999 * size.height + 1
//                                if (l[3] > size.height) size.height else l[3]

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 1.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetRawGraph()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight(0.2f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        drawLine(
                            color = Color.Green,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    if (state.value.scannedBluetooth.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Tidak ada perangkat",
                                    fontFamily = Kanit,
                                    fontSize = 16.sp,
                                    color = Color(0xFF787878)
                                )
                            }
                        }
                    }

                    items(
                        items = state.value.scannedBluetooth,
                        key = { it.address }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color(
                                        0xFF69BD6C
                                    )
                                    else Color(
                                        0xFFF0F0F0
                                    )
                                )
                                .clickable {
                                    if (state.value.isConnecting) {
                                        vm.stopRecord()
                                    } else {
                                        vm.connect(it.address)
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    modifier = Modifier
                                        .padding(bottom = 2.dp),
                                    text = it.name,
                                    fontFamily = Kanit,
                                    fontSize = 20.sp,
                                    color =
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color.White
                                    else Color.Black
                                )
                                Text(
                                    modifier = Modifier
                                        .padding(bottom = 2.dp),
                                    text = it.address,
                                    fontFamily = Kanit,
                                    fontSize = 16.sp,
                                    color =
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color(
                                        0xFFE6E6E6
                                    )
                                    else Color(0xFF999999)
                                )
                                if (state.value.isConnecting && it.address == state.value.selectedAddress) {
                                    Text(
                                        modifier = Modifier
                                            .padding(bottom = 2.dp),
                                        text = state.value.state,
                                        fontFamily = Kanit,
                                        fontSize = 12.sp,
                                        color = if (state.value.state != "bisa dimulai") Color(
                                            0xFFDA9D00
                                        ) else Color(
                                            0xFF3A9D3E
                                        )
                                    )
                                }
                            }
                            if (state.value.isConnecting && it.address == state.value.selectedAddress) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    color = Color(0xFFDA9D00),
                                    modifier = Modifier
                                        .size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .align(Alignment.BottomCenter),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.value.isConnected) {
                        Color(0xFF3A9D3E)
                    } else if (state.value.isDuringSession) {
                        Color(0xFFB60D0D)
                    } else {
                        Color(0xFF27477D)
                    }
                ),
                onClick = {
                    if (state.value.isScanning) {
                        vm.stopScan()
                    } else if (state.value.isDuringSession) {
                        vm.stopRecord()
                    } else if (state.value.isConnected) {
                        vm.startRecord()
                    } else {
                        vm.startScan()
                    }
                },
            ) {
                Text(
                    text = if (state.value.isScanning) "Stop scan"
                    else if (state.value.isConnected) "Mulai sesi"
                    else if (state.value.isDuringSession) "Stop sesi"
                    else "Mulai scan",
                    fontFamily = Kanit,
                    fontSize = 24.sp,
                    color = Color.White
                )
                if (state.value.isScanning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                    )
                }
            }
        }
    }
}
