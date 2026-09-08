package com.example.dithercam

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.dithercam.gl.CameraGLSurfaceView
import com.example.dithercam.theme.DitherCamTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DitherTemplate(
  val name: String,
  val pixelSize: Float,
  val strength: Float,
  val colors: List<String>,
  val matrixSize: Int = 4,
  val shape: Int = 0,
  val builtIn: Boolean = false
)

data class DitherShapeMode(
  val id: Int,
  val label: String,
  val glyph: String
)

private val DITHER_SHAPES =
listOf(
  DitherShapeMode(0, "SQUARE", "■"),
       DitherShapeMode(1, "CIRCLE", "●"),
       DitherShapeMode(2, "PLUS", "+"),
       DitherShapeMode(3, "CROSS", "×"),
       DitherShapeMode(4, "DIAMOND", "◆"),
       DitherShapeMode(5, "DOT", "·")
)

private fun shapeModeFor(
  id: Int
): DitherShapeMode =
DITHER_SHAPES.firstOrNull {
  it.id == id
} ?: DITHER_SHAPES[0]

data class DitherResolutionMode(
  val label: String,
  val rendererValue: Float,
  val portraitWidth: Int,
  val portraitHeight: Int
) {
  
  val portraitLabel: String
  get() =
  "${portraitWidth}×${portraitHeight}"
  
  val dots: Int
  get() =
  portraitWidth * portraitHeight
  
  val dotsFormatted: String
  get() =
  String.format(
    Locale.US,
    "%,d",
    dots
  )
}

private val DITHER_RESOLUTION_MODES =
listOf(
  DitherResolutionMode(
    "ULTRA",
    0.25f,
    480,
    640
  ),
  DitherResolutionMode(
    "FINE",
    0.33f,
    360,
    480
  ),
  DitherResolutionMode(
    "HIGH",
    0.50f,
    300,
    400
  ),
  DitherResolutionMode(
    "1×",
    1f,
    240,
    320
  ),
  DitherResolutionMode(
    "2×",
    2f,
    216,
    288
  ),
  DitherResolutionMode(
    "3×",
    3f,
    192,
    256
  ),
  DitherResolutionMode(
    "4×",
    4f,
    168,
    224
  ),
  DitherResolutionMode(
    "5×",
    5f,
    144,
    192
  ),
  DitherResolutionMode(
    "6×",
    6f,
    120,
    160
  ),
  DitherResolutionMode(
    "7×",
    7f,
    108,
    144
  ),
  DitherResolutionMode(
    "8×",
    8f,
    96,
    128
  )
)

private fun resolutionModeFor(
  value: Float
): DitherResolutionMode {
  
  return DITHER_RESOLUTION_MODES
  .minByOrNull {
    kotlin.math.abs(
      it.rendererValue - value
    )
  }
  ?: DITHER_RESOLUTION_MODES[3]
}

class MainActivity : ComponentActivity() {
  
  companion object {
    private const val TAG = "MainActivity"
    
    private const val PREFS_NAME =
    "dithercam_preferences"
    
    private const val CUSTOM_TEMPLATES_KEY =
    "custom_templates"
  }
  
  private lateinit var glView:
    CameraGLSurfaceView
    
    private var cameraStartRequested = false
    private var cameraStarted = false
    
    /*
     * Built-in presets.
     *
     * Palette order matters:
     * DARK -> LIGHT
     */
    private val builtInTemplates =
    listOf(
      
      DitherTemplate(
        name = "CLASSIC",
        pixelSize = 2f,
        strength = 1f,
        colors = listOf(
          "#000000",
          "#FFFFFF"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "NEWSPRINT",
        pixelSize = 1f,
        strength = 1f,
        colors = listOf(
          "#161616",
          "#F2EDE1"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "GREEN CRT",
        pixelSize = 2f,
        strength = 0.90f,
        colors = listOf(
          "#001A0B",
          "#005C2B",
          "#23B85C",
          "#B7FFCB"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "AMBER",
        pixelSize = 2f,
        strength = 0.90f,
        colors = listOf(
          "#120900",
          "#5E2E00",
          "#C66A00",
          "#FFD27A"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "CYBER",
        pixelSize = 3f,
        strength = 1f,
        colors = listOf(
          "#10002B",
          "#3C096C",
          "#7B2CBF",
          "#C77DFF",
          "#FF00FF"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "OCEAN",
        pixelSize = 3f,
        strength = 0.85f,
        colors = listOf(
          "#001219",
          "#005F73",
          "#0A9396",
          "#94D2BD",
          "#E9D8A6"
        ),
        builtIn = true
      ),
      
      DitherTemplate(
        name = "FIRE",
        pixelSize = 3f,
        strength = 1f,
        colors = listOf(
          "#140000",
          "#6A040F",
          "#D00000",
          "#F48C06",
          "#FFBA08",
          "#FFF3B0"
        ),
        builtIn = true
      )
    )
    
    private val permissionLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { granted ->
      
      if (granted) {
        requestCameraStart()
      }
    }
    
    override fun onCreate(
      savedInstanceState: Bundle?
    ) {
      super.onCreate(savedInstanceState)
      
      enableEdgeToEdge()
      
      glView =
      CameraGLSurfaceView(this)
      
      glView.renderer.setOnReadyListener {
        
        runOnUiThread {
          
          if (cameraStartRequested) {
            startCamera()
          }
        }
      }
      
      setContent {
        
        DitherCamTheme {
          CameraScreen()
        }
      }
      
      if (hasCameraPermission()) {
        requestCameraStart()
      } else {
        permissionLauncher.launch(
          Manifest.permission.CAMERA
        )
      }
    }
    
    @Composable
    private fun CameraScreen() {
      
      var settingsOpen by remember {
        mutableStateOf(false)
      }
      
      var pixelSize by remember {
        mutableFloatStateOf(2f)
      }
      
      var strength by remember {
        mutableFloatStateOf(1f)
      }
      
      var matrixSize by remember {
        mutableStateOf(4)
      }
      
      var shape by remember {
        mutableStateOf(0)
      }
      
      val palette =
      remember {
        mutableStateListOf(
          "#000000",
          "#FFFFFF"
        )
      }
      
      /*
       * Read saved custom presets once
       * when this composition starts.
       */
      val customTemplates =
      remember {
        mutableStateListOf<DitherTemplate>()
        .apply {
          addAll(
            loadCustomTemplates()
          )
        }
      }
      
      var saveDialogOpen by remember {
        mutableStateOf(false)
      }
      
      var newTemplateName by remember {
        mutableStateOf("")
      }
      
      var activeTemplate by remember {
        mutableStateOf<String?>(
          "CLASSIC"
        )
      }
      
      fun applyTemplate(
        template: DitherTemplate
      ) {
        
        pixelSize =
        template.pixelSize
        
        strength =
        template.strength
        
        matrixSize =
        template.matrixSize
        
        shape =
        template.shape
        
        palette.clear()
        palette.addAll(
          template.colors
        )
        
        glView.setPixelSize(
          pixelSize
        )
        
        glView.setDitherStrength(
          strength
        )
        
        glView.setDitherMatrix(
          matrixSize
        )
        
        glView.setDitherShape(
          shape
        )
        
        glView.setPalette(
          palette.toList()
        )
        
        activeTemplate =
        template.name
      }
      
      Box(
        modifier =
        Modifier.fillMaxSize()
      ) {
        
        AndroidView(
          factory = {
            glView
          },
          modifier =
          Modifier.fillMaxSize()
        )
        
        /*
         * TOP BAR
         */
        Row(
          modifier =
          Modifier
          .fillMaxWidth()
          .padding(
            top = 40.dp,
            start = 16.dp,
            end = 16.dp
          ),
          horizontalArrangement =
          Arrangement.SpaceBetween,
          verticalAlignment =
          Alignment.CenterVertically
        ) {
          
          Text(
            text = "DITHERCAM",
            color = Color.White,
            fontWeight =
            FontWeight.Black,
            fontSize = 18.sp,
            modifier =
            Modifier
            .background(
              Color.Black.copy(
                alpha = 0.55f
              ),
              RoundedCornerShape(
                12.dp
              )
            )
            .padding(
              horizontal = 12.dp,
              vertical = 7.dp
            )
          )
          
          Text(
            text =
            if (settingsOpen)
              "×"
              else
                "⚙",
               color = Color.White,
               fontSize = 25.sp,
               fontWeight =
               FontWeight.Bold,
               modifier =
               Modifier
               .background(
                 Color.Black.copy(
                   alpha = 0.60f
                 ),
                 CircleShape
               )
               .clickable {
                 
                 settingsOpen =
                 !settingsOpen
               }
               .padding(10.dp)
          )
        }
        
        /*
         * BOTTOM CAMERA HUD
         */
        Column(
          modifier =
          Modifier
          .align(
            Alignment.BottomCenter
          )
          .fillMaxWidth()
          .background(
            Color.Black.copy(
              alpha = 0.55f
            )
          )
          .padding(
            horizontal = 20.dp,
            vertical = 12.dp
          ),
          horizontalAlignment =
          Alignment.CenterHorizontally
        ) {
          
          Text(
            text =
            (activeTemplate
            ?: "CUSTOM") +
            "  •  " +
            resolutionModeFor(pixelSize).label +
            "  •  " +
            resolutionModeFor(pixelSize).portraitLabel +
            "  •  " +
            "${matrixSize}×${matrixSize} " +
            shapeModeFor(shape).glyph +
            "  •  " +
            "${palette.size} COLORS",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight =
            FontWeight.Bold
          )
          
          Spacer(
            Modifier.height(9.dp)
          )
          
          Row(
            modifier =
            Modifier.fillMaxWidth(),
              horizontalArrangement =
              Arrangement.SpaceEvenly,
              verticalAlignment =
              Alignment.CenterVertically
          ) {
            
            Text(
              text = "▣",
              color =
              Color.White.copy(
                alpha = 0.75f
              ),
              fontSize = 27.sp
            )
            
            /*
             * REAL FILTERED CAPTURE
             */
            Box(
              modifier =
              Modifier
              .size(68.dp)
              .border(
                3.dp,
                Color.White,
                CircleShape
              )
              .padding(6.dp)
              .background(
                Color.White,
                CircleShape
              )
              .clickable {
                capturePhoto()
              }
            )
            
            /*
             * Camera flip still reserved.
             */
            Text(
              text = "↻",
              color =
              Color.White.copy(
                alpha = 0.75f
              ),
              fontSize = 30.sp
            )
          }
        }
        
        if (settingsOpen) {
          
          SettingsPanel(
            pixelSize = pixelSize,
            strength = strength,
            matrixSize = matrixSize,
            shape = shape,
            palette = palette,
            builtInTemplates =
            builtInTemplates,
            customTemplates =
            customTemplates,
            activeTemplate =
            activeTemplate,
            
            onTemplateSelected = {
              applyTemplate(it)
            },
            
            onSaveRequested = {
              
              newTemplateName = ""
              saveDialogOpen = true
            },
            
            onDeleteTemplate = {
              template ->
              
              customTemplates.remove(
                template
              )
              
              saveCustomTemplates(
                customTemplates.toList()
              )
              
              if (
                activeTemplate ==
                template.name
              ) {
                activeTemplate =
                null
              }
            },
            
            onPixelSize = {
              
              pixelSize =
              it
              
              activeTemplate = null
              
              glView.setPixelSize(
                pixelSize
              )
            },
            
            onStrength = {
              
              strength = it
              activeTemplate = null
              
              glView
              .setDitherStrength(
                it
              )
            },
            
            onMatrixSize = {
              matrixSize = it
              activeTemplate = null
              glView.setDitherMatrix(it)
            },
            
            onShape = {
              shape = it
              activeTemplate = null
              glView.setDitherShape(it)
            },
            
            onPaletteChanged = {
              
              activeTemplate = null
              
              glView.setPalette(
                palette.toList()
              )
            },
            
            modifier =
            Modifier
            .align(
              Alignment.BottomCenter
            )
            .padding(
              bottom = 110.dp,
              start = 12.dp,
              end = 12.dp
            )
          )
        }
        
        /*
         * SAVE TEMPLATE OVERLAY
         */
        if (saveDialogOpen) {
          
          Box(
            modifier =
            Modifier
            .fillMaxSize()
            .background(
              Color.Black.copy(
                alpha = 0.60f
              )
            )
            .clickable {
              saveDialogOpen = false
            },
            contentAlignment =
            Alignment.Center
          ) {
            
            Column(
              modifier =
              Modifier
              .padding(24.dp)
              .fillMaxWidth()
              .background(
                Color(
                  0xFF161616
                ),
                RoundedCornerShape(
                  22.dp
                )
              )
              /*
               * Stops clicks inside
               * from closing overlay.
               */
              .clickable(
                onClick = {}
              )
              .padding(20.dp)
            ) {
              
              Text(
                text =
                "SAVE TEMPLATE",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight =
                FontWeight.Black
              )
              
              Spacer(
                Modifier.height(
                  14.dp
                )
              )
              
              OutlinedTextField(
                value =
                newTemplateName,
                onValueChange = {
                  newTemplateName =
                  it
                },
                label = {
                  Text(
                    "Template name"
                  )
                },
                singleLine = true,
                modifier =
                Modifier
                .fillMaxWidth()
              )
              
              Spacer(
                Modifier.height(
                  10.dp
                )
              )
              
              Text(
                text =
                resolutionModeFor(pixelSize).label +
                "  •  " +
                resolutionModeFor(pixelSize).portraitLabel +
                "  •  " +
                "${resolutionModeFor(pixelSize).dotsFormatted} DOTS  •  " +
                "${matrixSize}×${matrixSize}  •  " +
                shapeModeFor(shape).label +
                "  •  " +
                "${(strength * 100).toInt()}%  •  " +
                "${palette.size} colors",
                color =
                Color.White.copy(
                  alpha = 0.65f
                ),
                fontSize = 12.sp
              )
              
              Spacer(
                Modifier.height(
                  10.dp
                )
              )
              
              /*
               * Palette preview
               */
              Row(
                horizontalArrangement =
                Arrangement.spacedBy(
                  5.dp
                )
              ) {
                
                palette.forEach {
                  
                  Box(
                    modifier =
                    Modifier
                    .size(
                      24.dp
                    )
                    .background(
                      composeColor(
                        it
                      ),
                      CircleShape
                    )
                  )
                }
              }
              
              Spacer(
                Modifier.height(
                  18.dp
                )
              )
              
              Row(
                modifier =
                Modifier
                .fillMaxWidth(),
                  horizontalArrangement =
                  Arrangement.spacedBy(
                    8.dp
                  )
              ) {
                
                Button(
                  onClick = {
                    saveDialogOpen =
                    false
                  },
                  modifier =
                  Modifier
                  .weight(1f),
                       colors =
                       ButtonDefaults
                       .buttonColors(
                         containerColor =
                         Color.DarkGray
                       )
                ) {
                  
                  Text("CANCEL")
                }
                
                Button(
                  onClick = {
                    
                    val name =
                    newTemplateName
                    .trim()
                    
                    if (
                      name.isBlank()
                    ) {
                      
                      Toast
                      .makeText(
                        this@MainActivity,
                        "Give the template a name",
                        Toast.LENGTH_SHORT
                      )
                      .show()
                      
                      return@Button
                    }
                    
                    /*
                     * Prevent a custom
                     * preset from pretending
                     * to be a built-in.
                     */
                    val builtInExists =
                    builtInTemplates.any {
                      it.name.equals(
                        name,
                        ignoreCase =
                        true
                      )
                    }
                    
                    if (builtInExists) {
                      
                      Toast
                      .makeText(
                        this@MainActivity,
                        "That name is already built in",
                        Toast.LENGTH_SHORT
                      )
                      .show()
                      
                      return@Button
                    }
                    
                    val template =
                    DitherTemplate(
                      name =
                      name,
                      pixelSize =
                      pixelSize,
                      strength =
                      strength,
                      colors =
                      palette
                      .toList(),
                                   matrixSize =
                                   matrixSize,
                                   shape =
                                   shape,
                                   builtIn =
                                   false
                    )
                    
                    /*
                     * Same custom name?
                     * Replace the old one.
                     */
                    val oldIndex =
                    customTemplates
                    .indexOfFirst {
                      it.name.equals(
                        name,
                        ignoreCase =
                        true
                      )
                    }
                    
                    if (
                      oldIndex >= 0
                    ) {
                      customTemplates[
                        oldIndex
                      ] = template
                    } else {
                      customTemplates.add(
                        template
                      )
                    }
                    
                    saveCustomTemplates(
                      customTemplates
                      .toList()
                    )
                    
                    activeTemplate =
                    template.name
                    
                    saveDialogOpen =
                    false
                    
                    Toast
                    .makeText(
                      this@MainActivity,
                      "Template saved 🔥",
                      Toast.LENGTH_SHORT
                    )
                    .show()
                  },
                  modifier =
                  Modifier
                  .weight(1f)
                ) {
                  
                  Text("SAVE")
                }
              }
            }
          }
        }
      }
    }
    
    @Composable
    private fun SettingsPanel(
      pixelSize: Float,
      strength: Float,
      matrixSize: Int,
      shape: Int,
      palette: MutableList<String>,
      builtInTemplates:
      List<DitherTemplate>,
      customTemplates:
      List<DitherTemplate>,
      activeTemplate: String?,
      onTemplateSelected:
      (DitherTemplate) -> Unit,
                              onSaveRequested: () -> Unit,
                              onDeleteTemplate:
                              (DitherTemplate) -> Unit,
                              onPixelSize: (Float) -> Unit,
                              onStrength: (Float) -> Unit,
                              onMatrixSize: (Int) -> Unit,
                              onShape: (Int) -> Unit,
                              onPaletteChanged: () -> Unit,
                              modifier: Modifier =
                              Modifier
    ) {
      
      Column(
        modifier =
        modifier
        .fillMaxWidth()
        .heightIn(
          max = 430.dp
        )
        .background(
          Color.Black.copy(
            alpha = 0.92f
          ),
          RoundedCornerShape(
            22.dp
          )
        )
        .verticalScroll(
          rememberScrollState()
        )
        .padding(12.dp)
      ) {
        
        /*
         * =========================
         * TEMPLATES
         * =========================
         */
        Text(
          text = "TEMPLATES",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight =
          FontWeight.Black
        )
        
        Spacer(
          Modifier.height(9.dp)
        )
        
        Text(
          text = "BUILT IN",
          color =
          Color.White.copy(
            alpha = 0.55f
          ),
          fontSize = 10.sp,
          fontWeight =
          FontWeight.Bold
        )
        
        Spacer(
          Modifier.height(6.dp)
        )
        
        Row(
          modifier =
          Modifier
          .fillMaxWidth()
          .horizontalScroll(
            rememberScrollState()
          ),
          horizontalArrangement =
          Arrangement.spacedBy(
            7.dp
          )
        ) {
          
          builtInTemplates.forEach {
            template ->
            
            TemplateChip(
              template =
              template,
              selected =
              activeTemplate ==
              template.name,
              onClick = {
                onTemplateSelected(
                  template
                )
              }
            )
          }
        }
        
        if (
          customTemplates.isNotEmpty()
        ) {
          
          Spacer(
            Modifier.height(12.dp)
          )
          
          Text(
            text = "MY TEMPLATES",
            color =
            Color.White.copy(
              alpha = 0.55f
            ),
            fontSize = 10.sp,
            fontWeight =
            FontWeight.Bold
          )
          
          Spacer(
            Modifier.height(6.dp)
          )
          
          customTemplates.forEach {
            template ->
            
            Row(
              modifier =
              Modifier
              .fillMaxWidth()
              .padding(
                vertical = 3.dp
              ),
              verticalAlignment =
              Alignment.CenterVertically
            ) {
              
              Box(
                modifier =
                Modifier
                .weight(1f)
                .background(
                  if (
                    activeTemplate ==
                    template.name
                  )
                    Color.White
                    else
                      Color(
                        0xFF292929
                      ),
                      RoundedCornerShape(
                        12.dp
                      )
                )
                .clickable {
                  onTemplateSelected(
                    template
                  )
                }
                .padding(
                  horizontal =
                  12.dp,
                  vertical =
                  9.dp
                )
              ) {
                
                Row(
                  modifier =
                  Modifier
                  .fillMaxWidth(),
                    horizontalArrangement =
                    Arrangement
                    .SpaceBetween,
                    verticalAlignment =
                    Alignment
                    .CenterVertically
                ) {
                  
                  Text(
                    text =
                    template.name,
                    color =
                    if (
                      activeTemplate ==
                      template.name
                    )
                      Color.Black
                      else
                        Color.White,
                       fontSize =
                       12.sp,
                       fontWeight =
                       FontWeight.Bold
                  )
                  
                  Row(
                    horizontalArrangement =
                    Arrangement.spacedBy(
                      3.dp
                    )
                  ) {
                    
                    template.colors
                    .take(8)
                    .forEach {
                      
                      Box(
                        modifier =
                        Modifier
                        .size(
                          13.dp
                        )
                        .background(
                          composeColor(
                            it
                          ),
                          CircleShape
                        )
                      )
                    }
                  }
                }
              }
              
              Spacer(
                Modifier.width(5.dp)
              )
              
              /*
               * Custom only = deletable.
               */
              Text(
                text = "×",
                color = Color.White,
                fontSize = 22.sp,
                modifier =
                Modifier
                .clickable {
                  onDeleteTemplate(
                    template
                  )
                }
                .padding(8.dp)
              )
            }
          }
        }
        
        Spacer(
          Modifier.height(9.dp)
        )
        
        Button(
          onClick =
          onSaveRequested,
          modifier =
          Modifier.fillMaxWidth()
        ) {
          
          Text(
            text =
            "+ SAVE CURRENT"
          )
        }
        
        Spacer(
          Modifier.height(20.dp)
        )
        
        /*
         * =========================
         * BAYER CONTROLS
         * =========================
         */
        Text(
          text = "DITHER",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight =
          FontWeight.Black
        )
        
        Spacer(
          Modifier.height(12.dp)
        )
        
        Text(
          text = "DITHER RESOLUTION",
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
        
        Text(
          text =
          resolutionModeFor(pixelSize).label +
          "  •  " +
          resolutionModeFor(pixelSize).portraitLabel +
          "  •  " +
          resolutionModeFor(pixelSize).dotsFormatted +
          " DOTS",
          color = Color.White.copy(alpha = 0.62f),
             fontSize = 10.sp
        )
        
        Spacer(
          Modifier.height(6.dp)
        )
        
        Row(
          modifier =
          Modifier
          .fillMaxWidth()
          .horizontalScroll(
            rememberScrollState()
          ),
          horizontalArrangement =
          Arrangement.spacedBy(
            6.dp
          )
        ) {
          
          DITHER_RESOLUTION_MODES.forEach {
            mode ->
            
            val selected =
            resolutionModeFor(pixelSize) == mode
            
            Button(
              onClick = {
                onPixelSize(
                  mode.rendererValue
                )
              },
              modifier =
              Modifier.height(34.dp),
                   colors =
                   ButtonDefaults
                   .buttonColors(
                     containerColor =
                     if (selected)
                       Color.White
                       else
                         Color.DarkGray,
                         contentColor =
                         if (selected)
                           Color.Black
                           else
                             Color.White
                   )
            ) {
              
              Text(
                text = mode.label,
                fontSize = 10.sp,
                fontWeight =
                FontWeight.Bold
              )
            }
          }
        }
        
        Spacer(
          Modifier.height(9.dp)
        )
        
        Text(
          text = "BAYER MATRIX",
          color = Color.White,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        
        Spacer(
          Modifier.height(4.dp)
        )
        
        Row(
          modifier =
          Modifier.fillMaxWidth(),
            horizontalArrangement =
            Arrangement.spacedBy(6.dp)
        ) {
          listOf(2, 4, 8).forEach {
            size ->
            
            val selected =
            matrixSize == size
            
            Button(
              onClick = {
                onMatrixSize(size)
              },
              modifier =
              Modifier
              .weight(1f)
              .height(34.dp),
                   colors =
                   ButtonDefaults.buttonColors(
                     containerColor =
                     if (selected)
                       Color.White
                       else
                         Color.DarkGray,
                         contentColor =
                         if (selected)
                           Color.Black
                           else
                             Color.White
                   )
            ) {
              Text(
                text = "${size}×${size}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
        
        Spacer(
          Modifier.height(9.dp)
        )
        
        Text(
          text =
          "SHAPE  •  " +
          shapeModeFor(shape).label,
             color = Color.White,
             fontSize = 11.sp,
             fontWeight = FontWeight.Bold
        )
        
        Spacer(
          Modifier.height(4.dp)
        )
        
        Row(
          modifier =
          Modifier
          .fillMaxWidth()
          .horizontalScroll(
            rememberScrollState()
          ),
          horizontalArrangement =
          Arrangement.spacedBy(6.dp)
        ) {
          DITHER_SHAPES.forEach {
            mode ->
            
            val selected =
            shape == mode.id
            
            Button(
              onClick = {
                onShape(mode.id)
              },
              modifier =
              Modifier.height(34.dp),
                   colors =
                   ButtonDefaults.buttonColors(
                     containerColor =
                     if (selected)
                       Color.White
                       else
                         Color.DarkGray,
                         contentColor =
                         if (selected)
                           Color.Black
                           else
                             Color.White
                   )
            ) {
              Text(
                text =
                mode.glyph +
                " " +
                mode.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
        
        Spacer(
          Modifier.height(9.dp)
        )
        
        Text(
          text =
          "STRENGTH  " +
          "${(strength * 100).toInt()}%",
             color = Color.White,
             fontSize = 12.sp,
             fontWeight =
             FontWeight.Bold
        )
        
        Slider(
          value = strength,
          onValueChange =
          onStrength,
          valueRange =
          0f..1f,
          modifier =
          Modifier.height(30.dp)
        )
        
        /*
         * =========================
         * PALETTE
         * =========================
         */
        Text(
          text =
          "PALETTE  " +
          "${palette.size}/8",
          color = Color.White,
          fontSize = 12.sp,
          fontWeight =
          FontWeight.Bold
        )
        
        Text(
          text =
          "Dark → Light",
          color =
          Color.White.copy(
            alpha = 0.45f
          ),
          fontSize = 10.sp
        )
        
        Spacer(
          Modifier.height(6.dp)
        )
        
        palette.forEachIndexed {
          index,
          value ->
          
          Row(
            modifier =
            Modifier
            .fillMaxWidth()
            .padding(
              vertical = 3.dp
            ),
            verticalAlignment =
            Alignment.CenterVertically
          ) {
            
            Box(
              modifier =
              Modifier
              .size(26.dp)
              .background(
                composeColor(
                  value
                ),
                CircleShape
              )
              .border(
                1.dp,
                Color.White.copy(
                  alpha = 0.4f
                ),
                CircleShape
              )
            )
            
            Spacer(
              Modifier.width(8.dp)
            )
            
            OutlinedTextField(
              value = value,
              onValueChange = {
                newValue ->
                
                palette[index] =
                newValue
                
                onPaletteChanged()
              },
              singleLine = true,
              keyboardOptions =
              KeyboardOptions(
                capitalization =
                KeyboardCapitalization
                .Characters
              ),
              modifier =
              Modifier.weight(1f)
            )
            
            if (palette.size > 2) {
              
              Spacer(
                Modifier.width(5.dp)
              )
              
              Text(
                text = "×",
                color = Color.White,
                fontSize = 24.sp,
                modifier =
                Modifier
                .clickable {
                  
                  palette.removeAt(
                    index
                  )
                  
                  onPaletteChanged()
                }
                .padding(8.dp)
              )
            }
          }
        }
        
        if (palette.size < 8) {
          
          Spacer(
            Modifier.height(8.dp)
          )
          
          Button(
            onClick = {
              
              palette.add(
                "#808080"
              )
              
              onPaletteChanged()
            },
            modifier =
            Modifier.fillMaxWidth()
          ) {
            
            Text(
              text = "+ ADD COLOR"
            )
          }
        }
      }
    }
    
    @Composable
    private fun TemplateChip(
      template: DitherTemplate,
      selected: Boolean,
      onClick: () -> Unit
    ) {
      
      Column(
        modifier =
        Modifier
        .background(
          if (selected)
            Color.White
            else
              Color(
                0xFF292929
              ),
              RoundedCornerShape(
                12.dp
              )
        )
        .clickable {
          onClick()
        }
        .padding(
          horizontal = 12.dp,
          vertical = 9.dp
        )
      ) {
        
        Text(
          text = template.name,
          color =
          if (selected)
            Color.Black
            else
              Color.White,
             fontSize = 11.sp,
             fontWeight =
             FontWeight.Bold
        )
        
        Spacer(
          Modifier.height(5.dp)
        )
        
        Row(
          horizontalArrangement =
          Arrangement.spacedBy(
            3.dp
          )
        ) {
          
          template.colors
          .take(8)
          .forEach {
            
            Box(
              modifier =
              Modifier
              .size(12.dp)
              .background(
                composeColor(
                  it
                ),
                CircleShape
              )
            )
          }
        }
      }
    }
    
    /*
     * ==================================
     * CUSTOM TEMPLATE STORAGE
     * ==================================
     */
    
    private fun saveCustomTemplates(
      templates:
      List<DitherTemplate>
    ) {
      
      try {
        
        val array =
        JSONArray()
        
        templates.forEach {
          template ->
          
          val objectJson =
          JSONObject()
          
          objectJson.put(
            "name",
            template.name
          )
          
          objectJson.put(
            "pixelSize",
            template.pixelSize
          )
          
          objectJson.put(
            "strength",
            template.strength
          )
          
          objectJson.put(
            "matrixSize",
            template.matrixSize
          )
          
          objectJson.put(
            "shape",
            template.shape
          )
          
          val colorsJson =
          JSONArray()
          
          template.colors.forEach {
            color ->
            
            colorsJson.put(color)
          }
          
          objectJson.put(
            "colors",
            colorsJson
          )
          
          array.put(
            objectJson
          )
        }
        
        getSharedPreferences(
          PREFS_NAME,
          Context.MODE_PRIVATE
        )
        .edit()
        .putString(
          CUSTOM_TEMPLATES_KEY,
          array.toString()
        )
        .apply()
        
      } catch (e: Exception) {
        
        Log.e(
          TAG,
          "Failed to save templates",
          e
        )
      }
    }
    
    private fun loadCustomTemplates():
      List<DitherTemplate> {
        
        val result =
        mutableListOf<DitherTemplate>()
        
        try {
          
          val prefs =
          getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
          )
          
          val json =
          prefs.getString(
            CUSTOM_TEMPLATES_KEY,
            null
          ) ?: return emptyList()
          
          val array =
          JSONArray(json)
          
          for (
            i in 0 until array.length()
          ) {
            
            val objectJson =
            array.getJSONObject(i)
            
            val colorsJson =
            objectJson.getJSONArray(
              "colors"
            )
            
            val colors =
            mutableListOf<String>()
            
            for (
              c in 0 until
              colorsJson.length()
            ) {
              
              colors.add(
                colorsJson.getString(c)
              )
            }
            
            /*
             * Ignore corrupted presets
             * instead of feeding bad data
             * to the renderer.
             */
            if (
              colors.size !in 2..8
            ) {
              continue
            }
            
            result.add(
              DitherTemplate(
                name =
                objectJson.getString(
                  "name"
                ),
                pixelSize =
                objectJson
                .getDouble(
                  "pixelSize"
                )
                .toFloat(),
                             strength =
                             objectJson
                             .getDouble(
                               "strength"
                             )
                             .toFloat()
                             .coerceIn(
                               0f,
                               1f
                             ),
                             colors = colors,
                             matrixSize =
                             objectJson.optInt(
                               "matrixSize",
                               4
                             ).let {
                               if (it == 2 || it == 8) it else 4
                             },
                             shape =
                             objectJson.optInt(
                               "shape",
                               0
                             ).coerceIn(
                               0,
                               5
                             ),
                             builtIn = false
              )
            )
          }
          
        } catch (e: Exception) {
          
          Log.e(
            TAG,
            "Failed to load templates",
            e
          )
        }
        
        return result
      }
      
      /*
       * ==================================
       * CAPTURE
       * ==================================
       */
      
      private fun capturePhoto() {
        
        Toast.makeText(
          this,
          "Capturing…",
          Toast.LENGTH_SHORT
        ).show()
        
        glView.capture { bitmap ->
          
          saveBitmap(bitmap)
        }
      }
      
      private fun saveBitmap(
        bitmap: Bitmap
      ) {
        
        try {
          
          val timestamp =
          SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
          ).format(
            Date()
          )
          
          val name =
          "DitherCam_$timestamp.png"
          
          val values =
          ContentValues().apply {
            
            put(
              MediaStore.Images
              .Media.DISPLAY_NAME,
              name
            )
            
            put(
              MediaStore.Images
              .Media.MIME_TYPE,
              "image/png"
            )
            
            put(
              MediaStore.Images
              .Media.RELATIVE_PATH,
              "Pictures/DitherCam"
            )
            
            put(
              MediaStore.Images
              .Media.IS_PENDING,
              1
            )
          }
          
          val resolver =
          contentResolver
          
          val uri =
          resolver.insert(
            MediaStore.Images
            .Media
            .EXTERNAL_CONTENT_URI,
            values
          )
          ?: throw RuntimeException(
            "MediaStore insert failed"
          )
          
          resolver
          .openOutputStream(uri)
          .use { stream ->
            
            requireNotNull(stream)
            
            if (
              !bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream
              )
            ) {
              throw RuntimeException(
                "PNG compression failed"
              )
            }
          }
          
          values.clear()
          
          values.put(
            MediaStore.Images
            .Media.IS_PENDING,
            0
          )
          
          resolver.update(
            uri,
            values,
            null,
            null
          )
          
          bitmap.recycle()
          
          runOnUiThread {
            
            Toast.makeText(
              this,
              "Saved 🔥",
              Toast.LENGTH_SHORT
            ).show()
          }
          
        } catch (e: Exception) {
          
          Log.e(
            TAG,
            "Capture save failed",
            e
          )
          
          bitmap.recycle()
          
          runOnUiThread {
            
            Toast.makeText(
              this,
              "Couldn't save photo",
              Toast.LENGTH_SHORT
            ).show()
          }
        }
      }
      
      /*
       * ==================================
       * COLOR PREVIEW
       * ==================================
       */
      
      private fun composeColor(
        input: String
      ): Color {
        
        return try {
          
          var hex =
          input.trim()
          
          if (hex.startsWith("#")) {
            hex =
            hex.substring(1)
          }
          
          if (hex.length != 6) {
            return Color.Transparent
          }
          
          val rgb =
          hex.toLong(16)
          
          Color(
            red =
            ((rgb shr 16) and 255) /
            255f,
            green =
            ((rgb shr 8) and 255) /
            255f,
            blue =
            (rgb and 255) /
            255f
          )
          
        } catch (_: Exception) {
          Color.Transparent
        }
      }
      
      /*
       * ==================================
       * CAMERA
       * ==================================
       */
      
      private fun requestCameraStart() {
        
        cameraStartRequested = true
        
        if (
          glView.renderer.isReady()
        ) {
          startCamera()
        }
      }
      
      private fun startCamera() {
        
        if (cameraStarted) {
          return
        }
        
        if (
          !glView.renderer.isReady()
        ) {
          return
        }
        
        cameraStarted = true
        
        val future =
        ProcessCameraProvider
        .getInstance(this)
        
        future.addListener(
          {
            
            try {
              
              val provider =
              future.get()
              
              val rotation =
              display?.rotation
              ?: android.view
              .Surface
              .ROTATION_0
              
              val preview =
              Preview.Builder()
              .setTargetRotation(
                rotation
              )
              .build()
              
              preview.setSurfaceProvider {
                request ->
                
                val width =
                request
                .resolution
                .width
                
                val height =
                request
                .resolution
                .height
                
                request
                .setTransformationInfoListener(
                  ContextCompat
                  .getMainExecutor(
                    this
                  )
                ) { info ->
                  
                  Log.d(
                    TAG,
                    "rotation=" +
                    "${info.rotationDegrees}, " +
                    "crop=${info.cropRect}, " +
                    "mirror=${info.isMirroring}"
                  )
                }
                
                /*
                 * Known-good CameraX -> GL
                 * path. Leave it alone.
                 */
                glView.renderer
                .setCameraBufferSize(
                  width,
                  height
                )
                
                val surface =
                glView.renderer
                .getCameraSurface()
                
                request.provideSurface(
                  surface,
                  ContextCompat
                  .getMainExecutor(
                    this
                  )
                ) { result ->
                  
                  Log.d(
                    TAG,
                    "Surface result: " +
                    result.resultCode
                  )
                }
              }
              
              provider.unbindAll()
              
              provider.bindToLifecycle(
                this,
                CameraSelector
                .DEFAULT_BACK_CAMERA,
                preview
              )
              
            } catch (e: Exception) {
              
              cameraStarted = false
              
              Log.e(
                TAG,
                "Camera start failed",
                e
              )
            }
          },
          ContextCompat
          .getMainExecutor(this)
        )
      }
      
      private fun hasCameraPermission():
        Boolean {
          
          return ContextCompat
          .checkSelfPermission(
            this,
            Manifest.permission.CAMERA
          ) ==
          PackageManager
          .PERMISSION_GRANTED
        }
        
        override fun onDestroy() {
          
          if (::glView.isInitialized) {
            glView.onPause()
          }
          
          super.onDestroy()
        }
}
