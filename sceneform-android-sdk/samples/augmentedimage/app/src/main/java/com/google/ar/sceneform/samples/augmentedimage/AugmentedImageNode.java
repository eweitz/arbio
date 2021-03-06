/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.sceneform.samples.augmentedimage;

import android.content.Context;
import android.net.Uri;
import android.os.StrictMode;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.google.ar.core.AugmentedImage;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.animation.ModelAnimator;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.AnimationData;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Node for rendering an augmented image. The image is framed by placing the virtual picture frame
 * at the corners of the augmented image trackable.
 */
@SuppressWarnings({"AndroidApiChecker"})
public class AugmentedImageNode extends AnchorNode {

  private static final String TAG = "AugmentedImageNode";

  // The augmented image represented by this node.
  private AugmentedImage image;

  private final Context nodeContext;

  // eweitz: Renderable resources for Broad Institute AR lobby app
  private static CompletableFuture<ModelRenderable> whiteBloodCell;
  private static CompletableFuture<ModelRenderable> protein;
  private static CompletableFuture<ModelRenderable> brain;
  private static CompletableFuture<ModelRenderable> cesiumMan;
//  private static CompletableFuture<ModelRenderable> maccawAnimationFuture;
  private static CompletableFuture<ModelRenderable> maccawAnimation;
//  private static ModelRenderable maccawAnimation;
  private static CompletableFuture<ViewRenderable> ratGenome;

  private static final String GLTF_ASSET =
          "https://github.com/KhronosGroup/glTF-Sample-Models/raw/master/2.0/Duck/glTF/Duck.gltf";

  private static final String AR_ASSETS_CONFIG_URL =
          "https://storage.googleapis.com/arbio/ar-assets-config.json";

  // Controls animation playback.
  private static ModelAnimator animator;

  private static Boolean startedAnimator = false;

  // Index of the current animation playing.
  private int nextAnimation;

  private static Map<String, Object> assets;

  private static String readUrl(String urlString) throws Exception {
    BufferedReader reader = null;
    try {
      URL url = new URL(urlString);
      reader = new BufferedReader(new InputStreamReader(url.openStream()));
      StringBuffer buffer = new StringBuffer();
      int read;
      char[] chars = new char[1024];
      while ((read = reader.read(chars)) != -1)
        buffer.append(chars, 0, read);

      return buffer.toString();
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private static void fetchAssets() {
    String content = "";
    try {
      content = readUrl(AR_ASSETS_CONFIG_URL);
      Log.i(TAG, "Fetched AR assets configuration");
    } catch (Exception e) {
      Log.e(TAG, "Cannot fetch AR assets configuration", e);
    }

    Type type = new TypeToken<Map<String, Object>>(){}.getType();
    Map<String, Object> jsonContent = new Gson().fromJson(content, type);
    Log.i(TAG, "Parsed AR assets JSON:");
    Log.i(TAG, jsonContent.toString());

    Log.i(TAG, "Assets in JSON");
    Log.i(TAG, jsonContent.get("assets").toString());

    assets = (Map<String, Object>) jsonContent.get("assets");

  }

    /**
     * Retrieves and sets 3D model assets from GCS bucket
     *
     * TODO:
     * Refactor to do initial fetch on app start, not image recognition
     */
    private static void setAssets(Context context) {

        // TODO: Refactor to not fetch in main thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        fetchAssets();

        for (Map.Entry<String, Object> entry : assets.entrySet()) {

            Map<String, Object> modelAssets = new HashMap();

            Log.i(TAG, "entry:");
            Log.i(TAG, entry.toString());

            Map<String, Object> asset = (Map<String, Object>) entry.getValue();

            Log.i(TAG, "asset:");
            Log.i(TAG, asset.toString());

            ArrayList position;
            Double scale;
            ArrayList rotation;

            for (Map.Entry<String, Object> modelEntry : asset.entrySet()) {
                Map<String, Object> modelAsset = (Map<String, Object>) modelEntry.getValue();

                CompletableFuture<ModelRenderable> model;

                String urlString = modelAsset.get("url").toString();

                Uri uri = Uri.parse(urlString);

                if (modelAsset.containsKey("scale")) {
                    scale = (Double) modelAsset.get("scale");
                } else {
                    scale = 1.0;
                }

                if (modelAsset.containsKey("position")) {
                  position = (ArrayList) modelAsset.get("position");
                } else {
                  position = new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0));
                }

                if (modelAsset.containsKey("rotation")) {
                  rotation = (ArrayList) modelAsset.get("rotation");
                } else {
                  rotation = new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0));
                }

                if (isSfbFile(urlString)) {
                    model = ModelRenderable.builder()
                            .setSource(context, uri)
                            .setRegistryId(uri)
                            .build();
                } else {
                    model = ModelRenderable.builder()
                            .setSource(context, RenderableSource.builder().setSource(
                                    context,
                                    uri,
                                    RenderableSource.SourceType.GLTF2).build())
                            .setRegistryId(uri)
                            .build();
                }

                modelAsset.put("url", uri);
                modelAsset.put("position", position);
                modelAsset.put("scale", scale);
                modelAsset.put("rotation", rotation);
                modelAsset.put("model", model);

                modelAssets.put(modelEntry.getKey(), modelAsset);
            }

            assets.put(entry.getKey(), modelAssets);
        }
    }

  /**
   * Determines if a URL ends in "sfb", i.e. if it's for a Sceneform binary file
   * Used as a crude check for animation support.
   */
  private static Boolean isSfbFile(String url) {
    int urlLength = url.length();
    String endSlice = url.substring(urlLength - 3, urlLength);
    return endSlice.equals("sfb");
  }

  public AugmentedImageNode(Context context) {
    this.nodeContext = context;

    if (assets == null) {
      setAssets(context);
    }
  }

  /**
   * Called upon detecting a flat image atop the short stand at west in 415 Main lobby
   */
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setBroadLobbyImages(AugmentedImage image) {
    this.image = image;

    String imageName = image.getName();

    Map<String, Object> imageAssets = (Map<String, Object>) assets.get(imageName);

    for(Map.Entry<String, Object> entry : imageAssets.entrySet()) {

      Map<String, Object> asset = (Map<String, Object>) imageAssets.get(entry.getKey());

      CompletableFuture<ModelRenderable> model = (CompletableFuture<ModelRenderable>) asset.get("model");

      // If any of the models are not loaded, then recurse when all are loaded.
      if (!model.isDone()) {
        CompletableFuture.allOf(model)
                .thenAccept((Void aVoid) -> setBroadLobbyImages(image))
                .exceptionally(
                        throwable -> {
                          Log.e(TAG, "Exception loading", throwable);
                          return null;
                        });
        break;
      }

      // Set the anchor based on the center of the image.
      setAnchor(image.createAnchor(image.getCenterPose()));

      Vector3 localPosition = new Vector3();
      Node node;

      ArrayList position = (ArrayList) asset.get("position");
      Float x = ((Double) position.get(0)).floatValue();
      Float y = ((Double) position.get(1)).floatValue();
      Float z = ((Double) position.get(2)).floatValue();

      ArrayList rotation = (ArrayList) asset.get("rotation");
      Float rX = ((Double) rotation.get(0)).floatValue();
      Float rY = ((Double) rotation.get(1)).floatValue();
      Float rZ = ((Double) rotation.get(2)).floatValue();
      Float rW = ((Double) rotation.get(3)).floatValue();

      Float scale = ((Double) asset.get("scale")).floatValue();

      localPosition.set(x, y, z);
      node = new Node();
      node.setParent(this);
      node.setLocalPosition(localPosition);
      node.setLocalScale(new Vector3(scale, scale, scale));
      node.setLocalRotation(Quaternion.axisAngle(new Vector3(rX, rY, rZ), rW));
      node.setRenderable(model.getNow(null));


      if (isSfbFile(asset.get("url").toString())) {

        ModelRenderable animation = model.getNow(null);

        if ((animator == null || !animator.isRunning()) && startedAnimator == false) {
          startedAnimator = true;
          Log.i(TAG, "animator == null");
          AnimationData data = animation.getAnimationData(nextAnimation);
          nextAnimation = (nextAnimation + 1) % animation.getAnimationDataCount();
          animator = new ModelAnimator(data, animation);
          animator.start();
        }
      }
    }
  }

  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setProteinImage(AugmentedImage image) {
    this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!protein.isDone()) {
      CompletableFuture.allOf(whiteBloodCell)
              .thenAccept((Void aVoid) -> setProteinImage(image))
              .exceptionally(
                      throwable -> {
                        Log.e(TAG, "Exception loading", throwable);
                        return null;
                      });
    }
    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Make the 4 corner nodes.
    Vector3 localPosition = new Vector3();
    Node node;

    // Upper left corner.
    localPosition.set(-0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    node = new Node();
    node.setParent(this);
    node.setLocalPosition(localPosition);
    node.setRenderable(protein.getNow(null));
  }


  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setBrainImage(AugmentedImage image) {
    this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!brain.isDone()) {
      CompletableFuture.allOf(brain)
              .thenAccept((Void aVoid) -> setBrainImage(image))
              .exceptionally(
                      throwable -> {
                        Log.e(TAG, "Exception loading", throwable);
                        return null;
                      });
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));
    Vector3 localPosition = new Vector3();
    Node node;

    localPosition.set(-0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    node = new Node();
    node.setParent(this);
    node.setLocalPosition(localPosition);
    node.setRenderable(brain.getNow(null));
  }


  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setCesiumManImage(AugmentedImage image) {
    this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!cesiumMan.isDone()) {
      CompletableFuture.allOf(cesiumMan)
              .thenAccept((Void aVoid) -> setCesiumManImage(image))
              .exceptionally(
                      throwable -> {
                        Log.e(TAG, "Exception loading", throwable);
                        return null;
                      });
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Make the 4 corner nodes.
    Vector3 localPosition = new Vector3();
    Node node;

    // Upper left corner.
    localPosition.set(-0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    node = new Node();
    node.setParent(this);
    node.setLocalPosition(localPosition);
    node.setRenderable(cesiumMan.getNow(null));
  }

  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setMaccawAnimationImage(AugmentedImage image) {
    this.image = image;


    Log.i(TAG, "in setMaccawAnimationImage");

    if (!maccawAnimation.isDone()) {
      Log.i(TAG, "maccawAnimation is not done, waiting");
      CompletableFuture.allOf(maccawAnimation)
              .thenAccept((Void aVoid) -> {
                Log.i(TAG, "maccawAnimation is done");
                setMaccawAnimationImage(image);
              })
              .exceptionally(
                      throwable -> {
                          Log.e(TAG, "Exception loading", throwable);
                          return null;
                      });
      return;
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Make the 4 corner nodes.
    Vector3 localPosition = new Vector3();
    Node node;

    ModelRenderable maccaw = maccawAnimation.getNow(null);

    Log.i(TAG, "Got maccaw:");
    Log.i(TAG, maccaw.toString());

    node = new Node();
    node.setParent(this);
    node.setLocalPosition(localPosition);
    node.setRenderable(maccaw);

    Log.i(TAG, "startedAnimator:");
    Log.i(TAG, startedAnimator.toString());

    if ((animator == null || !animator.isRunning()) && startedAnimator == false) {
      startedAnimator = true;
      Log.i(TAG, "animator == null");
      AnimationData data = maccaw.getAnimationData(nextAnimation);
      nextAnimation = (nextAnimation + 1) % maccaw.getAnimationDataCount();
      animator = new ModelAnimator(data, maccaw);
      animator.start();
    }
  }

  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setRatGenomeImage(AugmentedImage image) {
    this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!ratGenome.isDone()) {
      CompletableFuture.allOf(ratGenome)
              .thenAccept((Void aVoid) -> setRatGenomeImage(image))
              .exceptionally(
                      throwable -> {
                        Log.e(TAG, "Exception loading", throwable);
                        return null;
                      });
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Make the 4 corner nodes.
    Vector3 localPosition = new Vector3();
    Node node;

    // Upper left corner.
    localPosition.set(-0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    node = new Node();
    node.setParent(this);
    node.setLocalPosition(localPosition);
    node.setRenderable(ratGenome.getNow(null));
  }


  public AugmentedImage getImage() {
    return image;
  }
}
