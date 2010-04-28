package com.evancharlton.mileage;

import java.util.ArrayList;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.evancharlton.mileage.dao.Dao;
import com.evancharlton.mileage.dao.Field;
import com.evancharlton.mileage.dao.Fillup;
import com.evancharlton.mileage.dao.FillupField;
import com.evancharlton.mileage.dao.FillupSeries;
import com.evancharlton.mileage.dao.Vehicle;
import com.evancharlton.mileage.dao.Dao.InvalidFieldException;
import com.evancharlton.mileage.math.Calculator;
import com.evancharlton.mileage.provider.FillUpsProvider;
import com.evancharlton.mileage.provider.Settings;
import com.evancharlton.mileage.provider.tables.FieldsTable;
import com.evancharlton.mileage.provider.tables.FillupsTable;
import com.evancharlton.mileage.provider.tables.VehiclesTable;
import com.evancharlton.mileage.views.CursorSpinner;
import com.evancharlton.mileage.views.DateButton;
import com.evancharlton.mileage.views.FieldView;

public class FillupActivity extends BaseFormActivity {
	private EditText mOdometer;
	private EditText mVolume;
	private EditText mPrice;
	private DateButton mDate;
	private CursorSpinner mVehicles;
	private CheckBox mPartial;
	private LinearLayout mFieldsContainer;
	private final ArrayList<FieldView> mFields = new ArrayList<FieldView>();
	private final Fillup mFillup = new Fillup(new ContentValues());

	private Bundle mIcicle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.fillup);
		// save the icicle so that we can restore the meta fields later on.
		mIcicle = savedInstanceState;
	}

	@Override
	protected void onResume() {
		super.onResume();

		Cursor fields = managedQuery(Uri.withAppendedPath(FillUpsProvider.BASE_URI, FieldsTable.FIELDS_URI), FieldsTable.getFullProjectionArray(),
				null, null, null);
		LayoutInflater inflater = LayoutInflater.from(this);
		mFieldsContainer.removeAllViews();
		while (fields.moveToNext()) {
			String hint = fields.getString(fields.getColumnIndex(Field.TITLE));
			long id = fields.getLong(fields.getColumnIndex(Field._ID));
			View container = inflater.inflate(R.layout.fillup_field, null);
			FieldView field = (FieldView) container.findViewById(R.id.field);
			field.setFieldId(id);
			field.setId((int) id);
			field.setHint(hint);
			mFieldsContainer.addView(container);
			mFields.add(field);

			if (mIcicle != null) {
				String value = mIcicle.getString(field.getKey());
				if (value != null) {
					field.setText(value);
				}
			}
		}
		if (fields.getCount() == 0) {
			mFieldsContainer.setVisibility(View.GONE);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		for (FieldView fieldView : mFields) {
			outState.putString(fieldView.getKey(), fieldView.getText().toString());
		}
	}

	@Override
	protected boolean postSaveValidation() {
		try {
			for (FieldView fieldView : mFields) {
				FillupField field = new FillupField(new ContentValues());
				field.setFillupId(mFillup.getId());
				field.setTemplateId(fieldView.getFieldId());
				field.setValue(fieldView.getText().toString());
				field.save(this);
			}
			return true;
		} catch (InvalidFieldException exception) {
			Toast.makeText(this, getString(exception.getErrorMessage()), Toast.LENGTH_LONG).show();
		}
		return false;
	}

	@Override
	protected void saved() {
		if (getParent() == null) {
			finish();
		} else {
			startActivity(new Intent(this, FillupListActivity.class));
			// reset the UI
			mOdometer.setText("");
			mVolume.setText("");
			mPrice.setText("");
			mPartial.setChecked(false);
		}
	}

	@Override
	protected Dao getDao() {
		return mFillup;
	}

	@Override
	protected String[] getProjectionArray() {
		return FillupsTable.getFullProjectionArray();
	}

	@Override
	protected Uri getUri(long id) {
		return ContentUris.withAppendedId(Uri.withAppendedPath(FillUpsProvider.BASE_URI, FillupsTable.FILLUP_URI), id);
	}

	@Override
	protected void initUI() {
		mOdometer = (EditText) findViewById(R.id.odometer);
		mVolume = (EditText) findViewById(R.id.volume);
		mPrice = (EditText) findViewById(R.id.price);
		mDate = (DateButton) findViewById(R.id.date);
		mPartial = (CheckBox) findViewById(R.id.partial);
		mFieldsContainer = (LinearLayout) findViewById(R.id.container);
		mVehicles = (CursorSpinner) findViewById(R.id.vehicle);
	}

	@Override
	protected void populateUI() {
		mOdometer.setText(String.valueOf(mFillup.getOdometer()));
		mVolume.setText(String.valueOf(mFillup.getVolume()));
		mPrice.setText(String.valueOf(mFillup.getUnitPrice()));
		mDate.setDate(mFillup.getTimestamp());
		mPartial.setChecked(mFillup.isPartial());

		SharedPreferences preferences = getSharedPreferences(Settings.NAME, Context.MODE_PRIVATE);
	}

	@Override
	protected void setFields() {
		// TODO: handle the case for input preferences
		try {
			mFillup.setVolume(Double.parseDouble(mVolume.getText().toString()));
		} catch (NumberFormatException e) {
			throw new InvalidFieldException(R.string.error_no_volume_specified);
		}

		try {
			mFillup.setUnitPrice(Double.parseDouble(mPrice.getText().toString()));
		} catch (NumberFormatException e) {
			throw new InvalidFieldException(R.string.error_no_price_specified);
		}

		try {
			// TODO: handle the + prefix
			mFillup.setOdometer(Double.parseDouble(mOdometer.getText().toString()));
		} catch (NumberFormatException e) {
			throw new InvalidFieldException(R.string.error_no_odometer_specified);
		}

		mFillup.setPartial(mPartial.isChecked());
		mFillup.setVehicleId(mVehicles.getSelectedItemId());

		// update the economy number
		Uri vehicleUri = ContentUris.withAppendedId(Uri.withAppendedPath(FillUpsProvider.BASE_URI, VehiclesTable.VEHICLE_URI), mVehicles
				.getSelectedItemId());

		Vehicle v = null;
		Cursor vehicleCursor = managedQuery(vehicleUri, VehiclesTable.getFullProjectionArray(), null, null, null);
		if (vehicleCursor.getCount() == 1) {
			vehicleCursor.moveToFirst();
			v = new Vehicle(vehicleCursor);
			Fillup previous = null;
			if (mFillup.isExistingObject()) {
				previous = mFillup.loadPrevious(this);
			} else {
				previous = v.loadLatestFillup(this);
			}
			if (previous == null) {
				mFillup.setEconomy(0D);
			} else {
				double economy = Calculator.averageEconomy(v, new FillupSeries(previous, mFillup));
				mFillup.setEconomy(economy);
			}
		}
	}

	@Override
	protected int getCreateString() {
		return R.string.add_fillup;
	}
}
