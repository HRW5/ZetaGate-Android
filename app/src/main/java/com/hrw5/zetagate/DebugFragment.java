package com.hrw5.zetagate;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class DebugFragment extends Fragment {
    private String serialLog = "";


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_debug, container, false);

        TextView displayMessage = view.findViewById(R.id.text_serial_data);
        displayMessage.setText(serialLog);

        return view;
    }

    public void updateSerialTextView(String text) {
        serialLog = serialLog + text + "\n";
        displaySerialTextView();
    }

    public void displaySerialTextView(){
        if(this.isVisible()) {
            TextView displayMessage = getView().findViewById(R.id.text_serial_data);

            displayMessage.setText(serialLog);
        }
    }
}
