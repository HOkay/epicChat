package com.lbros.epicchat;

import java.lang.ref.WeakReference;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

public class Utils {
	public static Bitmap getBitmapWithRoundedCorners(Bitmap bitmap, int radius) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
		final RectF rectF = new RectF(rect);

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, radius, radius, paint);

		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}
	
	public static BitmapFactory.Options getImageInformation(String imagePath){
		BitmapFactory.Options options = new BitmapFactory.Options();		//This options object will be passed to the decode() function
		options.inJustDecodeBounds = true;									//Forces the decode() function to only return basic information about the image, not the image's contents
		BitmapFactory.decodeFile(imagePath, options);
		return options;
	}
	
	public static int calculateSubSamplingFactor(BitmapFactory.Options options, int reqWidth, int reqHeight, boolean shrinkImage) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;
	
	    if (height > reqHeight || width > reqWidth) {
	
	        // Calculate ratios of height and width to requested height and width
	        final float heightRatio = ((float) height / (float) reqHeight);
	        final float widthRatio = ((float) width / (float) reqWidth);
	
	       if(shrinkImage){		//If this parameter is true, take the smallest ratio. This will mean that both dimensions are under the limits provided
	        	inSampleSize = (int) Math.ceil(heightRatio > widthRatio ? heightRatio : widthRatio);
	        }
	        else{				//Otherwise, take the largest ratio, which will mean that both the dimensions are over the limits
		        inSampleSize = (int) Math.floor(heightRatio < widthRatio ? heightRatio : widthRatio);
	        }
	    }
        return inSampleSize;
	}
	
	static class LoadBitmapAsync extends AsyncTask<Void, Void, Bitmap>{
		//private final String TAG = "LoadBitmapAsync";
		private String imagePath;
		private String imagePathFull;
		private final WeakReference<ImageView> imageViewReference;		//The ImageView is referenced from this to prevent it being garbage collected
		private Integer width = null;
		private Integer height = null;
		private boolean fade = false;
		private LruCache<String, Bitmap> cache = null;
		
		public LoadBitmapAsync(String newImagePath, ImageView imageView, Integer desiredWidth, Integer desiredHeight, boolean fadeIn, LruCache<String, Bitmap> newCache){
			imagePath = newImagePath;
			imagePathFull = imagePath+desiredWidth+desiredHeight;			//For the caching
			imageViewReference = new WeakReference<ImageView>(imageView);
			width = desiredWidth;
			height = desiredHeight;
			fade = fadeIn;
			cache = newCache;
		}
		@Override
		protected Bitmap doInBackground(Void...params){
			//The image we load will be downsampled when loading to reduce the memory footprint. To do this, we need to know the factor by which to downsample
			BitmapFactory.Options options = getImageInformation(imagePath);
			float originalWidth = options.outWidth;				//Original dimensions of the image
			float originalHeight = options.outHeight;
			
			//Load the Bitmap from the file. This is normally the part that takes the most time
			Bitmap outputImage = null;

			//Calculate the size of the desired image, as some information may be missing (maybe only the width or height was provided). Once we know this information, we can calculate the scaling factor
			if(width==null && height==null){		//No scaling, simply return the image as it was loaded from disk
				width = (int) originalWidth;
				height = (int) originalHeight;
			}
			else if(width!=null){
				height = (int) (originalHeight / originalWidth * width);
			}
			else if(height!=null){
				width = (int) (originalWidth / originalHeight * height);
			}
			int scalingFactor = calculateSubSamplingFactor(options, width, height, false);
			options.inSampleSize = scalingFactor;
			options.inJustDecodeBounds = false;		//We want the Bitmap's pixels this time
			outputImage = BitmapFactory.decodeFile(imagePath, options);				//This will load the bitmap and subsample with the value we provided, to minimise memory usage
			//Log.d(TAG, "width: "+outputImage.getWidth()+", height: "+outputImage.getHeight()+", Owidth: "+originalWidth+", Oheight: "+originalHeight);
			if(cache!=null && outputImage!=null){		//If a cache reference was provided, store the loaded image in the cache
				cache.put(imagePathFull, outputImage);
			}
			//Log.d(TAG, "IMAGE BYTES: "+outputImage.getByteCount());
			return outputImage;
		}
		
		protected void onPostExecute(Bitmap imageBitmap){
			if(imageViewReference!=null && imageBitmap!=null) {
				final ImageView imageView = imageViewReference.get();
	            if (imageView != null) {
	            	imageView.setImageBitmap(imageBitmap);
	            	if(fade){
	            		imageView.setVisibility(View.INVISIBLE);
	            		Animation fadeInAnimation = AnimationUtils.loadAnimation(imageView.getContext(), R.anim.animation_fade_in);
	            		imageView.startAnimation(fadeInAnimation);
	            		fadeInAnimation.setAnimationListener(new AnimationListener() {
	            	        @Override
	            	        public void onAnimationEnd(Animation arg0) {

	            	        	imageView.setVisibility(View.VISIBLE);


	            	        }

	            	        @Override
	            	        public void onAnimationRepeat(Animation animation) {}
	            	        @Override
	            	        public void onAnimationStart(Animation animation) {}
	            	    });
	            	}
	            }
			}
		}
	}
}