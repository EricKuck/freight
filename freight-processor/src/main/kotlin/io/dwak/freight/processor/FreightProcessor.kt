package io.dwak.freight.processor

import com.google.auto.service.AutoService
import io.dwak.Extra
import java.io.IOException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

@AutoService(value = Processor::class)
open class FreightProcessor : AbstractProcessor() {

  private lateinit var filer: Filer
  private lateinit var messager: Messager
  private lateinit var elementUtils: Elements

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    this.filer = processingEnv.filer
    this.messager = processingEnv.messager
    this.elementUtils = processingEnv.elementUtils
  }

  override fun getSupportedAnnotationTypes() = mutableSetOf(Extra::class.java.canonicalName)
  override fun getSupportedSourceVersion() = SourceVersion.latestSupported()

  override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {

    if (annotations.isNotEmpty()) {
      val freightTrainTargetClassMap = hashMapOf<TypeElement, FreightTrainBindingClass>()
      val builderTargetClassMap = hashMapOf<TypeElement, BuilderBindingClass>()
      val erasedTargetNames = mutableSetOf<String>()

      annotations.forEach {
        typeElement: TypeElement ->
        roundEnv.getElementsAnnotatedWith(typeElement)
                .forEach {
                  try {
                    if (it.kind != ElementKind.FIELD) {
                      error(it, "Extra can only be applied to fields!")
                      return false
                    }

                    val enclosingElement = it.enclosingElement as TypeElement
                    val shipperClass = getOrCreateFreightTrain(freightTrainTargetClassMap, enclosingElement, erasedTargetNames)
                    shipperClass.createAndAddBinding(it)

                    val builderClass = getOrCreateBuilder(builderTargetClassMap, enclosingElement, erasedTargetNames)
                    builderClass.createAndAddBinding(it)
                  } catch (e: Exception) {

                  }
                }
      }

      freightTrainTargetClassMap.values
              .plus(builderTargetClassMap.values)
              .forEach {
                try {
                  it.writeToFiler(filer)
                } catch (e: IOException) {
                  messager.printMessage(Diagnostic.Kind.ERROR, e.message)
                }
              }
    }

    return true
  }

  private fun getOrCreateFreightTrain(targetClassMap: MutableMap<TypeElement, FreightTrainBindingClass>,
                                      enclosingElement: TypeElement,
                                      erasedTargetNames: MutableSet<String>)
          : FreightTrainBindingClass {
    var freightTrainClass = targetClassMap[enclosingElement]
    if (freightTrainClass == null) {
      val targetClass = enclosingElement.qualifiedName.toString()
      val classPackage = enclosingElement.packageName(elementUtils)
      val className = enclosingElement.className(classPackage) + FreightTrainBindingClass.CLASS_SUFFIX
      freightTrainClass = FreightTrainBindingClass(classPackage, className, targetClass, processingEnv)
      targetClassMap.put(enclosingElement, freightTrainClass)
      erasedTargetNames.add(enclosingElement.toString())
    }

    return freightTrainClass
  }

  private fun getOrCreateBuilder(targetClassMap: MutableMap<TypeElement, BuilderBindingClass>,
                                 enclosingElement: TypeElement,
                                 erasedTargetNames: MutableSet<String>)
          : BuilderBindingClass {
    var builderClass = targetClassMap[enclosingElement]
    if (builderClass == null) {
      val targetClass = enclosingElement.qualifiedName.toString()
      val classPackage = enclosingElement.packageName(elementUtils)
      val className = enclosingElement.className(classPackage) + BuilderBindingClass.CLASS_SUFFIX
      builderClass = BuilderBindingClass(classPackage, className, targetClass, processingEnv)
      targetClassMap.put(enclosingElement, builderClass)
      erasedTargetNames.add(enclosingElement.toString())
    }

    return builderClass
  }

  private fun error(element: Element, message: String, vararg args: Any) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(message, args), element)
  }

  private fun note(note: String) {
    messager.printMessage(Diagnostic.Kind.NOTE, note)
  }
}

