package com.serenegiant.glutils;

/*
 * Copyright (c) 2014 saki t_saki@serenegiant.com
 *
 * File name: GLTexture.java
 *
*/

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.text.TextUtils;

import java.io.IOException;

import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL|ESのテクスチャ操作用のヘルパークラス
 */
public class GLTextureES1 {
//	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
//	private static final String TAG = "GLTexture";

	private final GL10 mGl;
	/* package */int mTextureTarget = GL10.GL_TEXTURE_2D;	// GL_TEXTURE_EXTERNAL_OESはだめ
	/* package */int mTextureUnit = GL10.GL_TEXTURE0;
	/* package */int mTextureId;
	/* package */final float[] mTexMatrix = new float[16];	// テクスチャ変換行列
	/* package */int mTexWidth, mTexHeight;
	/* package */int mImageWidth, mImageHeight;

	/**
	 * コンストラクタ
	 * @param width テクスチャサイズ
	 * @param height テクスチャサイズ
	 * @param filter_param	テクスチャの補間方法を指定 GL_LINEARとかGL_NEAREST
	 */
	public GLTextureES1(final GL10 gl, final int width, final int height, final int filter_param) {
//		if (DEBUG) Log.v(TAG, String.format("コンストラクタ(%d,%d)", width, height));
		mGl = gl;
		// テクスチャに使うビットマップは縦横サイズが2の乗数でないとダメ。
		// 更に、ミップマップするなら正方形でないとダメ
		// 指定したwidth/heightと同じか大きい2の乗数にする
		int w = 32;
		for (; w < width; w <<= 1);
		int h = 32;
		for (; h < height; h <<= 1);
		if (mTexWidth != w || mTexHeight != h) {
			mTexWidth = w;
			mTexHeight = h;
		}
//		if (DEBUG) Log.v(TAG, String.format("texSize(%d,%d)", mTexWidth, mTexHeight));
		mTextureId = GL1Helper.initTex(gl, mTextureTarget, filter_param);
		// テクスチャのメモリ領域を確保する
		gl.glTexImage2D(mTextureTarget,
			0,							// ミップマップレベル0(ミップマップしない)
			GL10.GL_RGBA,				// 内部フォーマット
			mTexWidth, mTexHeight,		// サイズ
			0,							// 境界幅
			GL10.GL_RGBA,				// 引き渡すデータのフォーマット
			GL10.GL_UNSIGNED_BYTE,		// データの型
			null);						// ピクセルデータ無し
		// テクスチャ変換行列を初期化
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = width / (float)mTexWidth;
		mTexMatrix[5] = height / (float)mTexHeight;
//		if (DEBUG) Log.v(TAG, "GLTexture:id=" + mTextureId);
	}

	@Override
	protected void finalize() throws Throwable {
		release();	// GLコンテキスト内じゃない可能性があるのであまり良くないけど
		super.finalize();
	}

	/**
	 * テクスチャを破棄
	 * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出すこと
	 */
	public void release() {
//		if (DEBUG) Log.v(TAG, "release:");
		if (mTextureId > 0) {
			GLHelper.deleteTex(mTextureId);
			mTextureId = 0;
		}
	}

	/**
	 * このインスタンスで管理しているテクスチャを有効にする(バインドする)
	 */
	public void bind() {
//		if (DEBUG) Log.v(TAG, "bind:");
		mGl.glActiveTexture(mTextureUnit);	// テクスチャユニットを選択
		mGl.glBindTexture(mTextureTarget, mTextureId);
	}

	/**
	 * このインスタンスで管理しているテクスチャを無効にする(アンバインドする)
	 */
	public void unbind() {
//		if (DEBUG) Log.v(TAG, "unbind:");
		mGl.glBindTexture(mTextureTarget, 0);
	}

	/**
	 * テクスチャターゲットを取得(GL_TEXTURE_2D)
	 * @return
	 */
	public int getTexTarget() { return mTextureTarget; }
	/**
	 * テクスチャ名を取得
	 * @return
	 */
	public int getTexture() { return mTextureId; }
	/**
	 * テクスチャ座標変換行列を取得(内部配列をそのまま返すので変更時は要注意)
	 * @return
	 */
	public float[] getTexMatrix() { return mTexMatrix; }
	/**
	 * テクスチャ座標変換行列のコピーを取得
	 * @param matrix 領域チェックしていないのでoffset位置から16個以上確保しておくこと
	 * @param offset
	 */
	public void getTexMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mTexMatrix, 0, matrix, offset, mTexMatrix.length);
	}
	/**
	 * テクスチャ幅を取得
	 * @return
	 */
	public int getTexWidth() { return mTexWidth; }
	/**
	 * テクスチャ高さを取得
	 * @return
	 */
	public int getTexHeight() { return mTexHeight; }

	/**
	 * 指定したファイルから画像をテクスチャに読み込む
	 * ファイルが存在しないか読み込めなければIOException/NullPointerExceptionを生成
	 * @param filePath
	 */
	public void loadTexture(final String filePath) throws NullPointerException, IOException {
//		if (DEBUG) Log.v(TAG, "loadTexture:path=" + filePath);
		if (TextUtils.isEmpty(filePath))
			throw new NullPointerException("image file path should not be a null");
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;	// Bitmapを生成せずにサイズ等の情報だけを取得する
		BitmapFactory.decodeFile(filePath, options);
		// テキスチャサイズ内に指定したイメージが収まるためのサブサンプリングを値を求める
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;
		int inSampleSize = 1;	// サブサンプリングサイズ
		if ((imageHeight > mTexHeight) || (imageWidth > mTexWidth)) {
			if (imageWidth > imageHeight) {
				inSampleSize = (int)Math.ceil(imageHeight / (float)mTexHeight);
			} else {
				inSampleSize = (int)Math.ceil(imageWidth / (float)mTexWidth);
			}
		}
//		if (DEBUG) Log.v(TAG, String.format("image(%d,%d),tex(%d,%d),inSampleSize=%d", imageWidth, imageHeight, mTexWidth, mTexHeight, inSampleSize));
		// 実際の読み込み処理
		options.inSampleSize = inSampleSize;
		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
		mImageWidth = bitmap.getWidth();	// 読み込んだイメージのサイズを取得
		mImageHeight = bitmap.getHeight();
		Bitmap texture = Bitmap.createBitmap(mTexWidth, mTexHeight, Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(texture);
		canvas.drawBitmap(bitmap, 0, 0, null);
		bitmap.recycle();
		bitmap = null;
		// テクスチャ座標変換行列を設定(読み込んだイメージサイズがテクスチャサイズにフィットするようにスケール変換)
		Matrix.setIdentityM(mTexMatrix, 0);
		mTexMatrix[0] = mImageWidth / (float)mTexWidth;
		mTexMatrix[5] = mImageHeight / (float)mTexHeight;
//		if (DEBUG) Log.v(TAG, String.format("image(%d,%d),scale(%f,%f)", mImageWidth, mImageHeight, mMvpMatrix[0], mMvpMatrix[5]));
		bind();
		GLUtils.texImage2D(mTextureTarget, 0, texture, 0);
		unbind();
		texture.recycle();
		texture = null;
	}
}
