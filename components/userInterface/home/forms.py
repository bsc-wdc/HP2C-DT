from django import forms


class CategoricalDeviceForm(forms.Form):
    def __init__(self, device, *args, **kwargs):
        super(CategoricalDeviceForm, self).__init__(*args, **kwargs)

        self.fields['device_id'] = forms.CharField(widget=forms.HiddenInput(),
                                                   initial=device.id)
        for i in range(1, device.size + 1):
            self.fields[f'phase_{i}'] = forms.ChoiceField(label=f'Phase {i}:',
                                                          choices=[('NULL', '---')] + [
                                                              (category, category)
                                                              for category in device.categories
                                                              if category != 'NULL']
                                                          )


class NonCategoricalDeviceForm(forms.Form):
    def __init__(self, device, *args, **kwargs):
        super(NonCategoricalDeviceForm, self).__init__(*args, **kwargs)
        self.device = device

        self.fields['device_id'] = forms.CharField(widget=forms.HiddenInput(),
                                                   initial=device.id)
        if "Generator" == device.type:
            self.fields['phase_1'] = forms.CharField(
                label='Voltage Setpoint',
                max_length=100,
                required=False)
            self.fields['phase_2'] = forms.CharField(
                label='Power Setpoint',
                max_length=100,
                required=False)
        else:
            for i in range(1, device.size + 1):
                self.fields[f'phase_{i}'] = forms.CharField(label=f'Phase {i}',
                                                            max_length=100,
                                                            required=False)

