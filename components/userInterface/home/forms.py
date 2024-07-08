from django import forms
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.models import User
from .models import Machine, Key_Gen
from django.core.exceptions import ValidationError
from django_recaptcha.fields import ReCaptchaField
from django_recaptcha.widgets import ReCaptchaV2Checkbox


class CreateUserForm(UserCreationForm):
    class Meta:
        model = User
        fields = ['username', 'email', 'password1', 'password2']

    #captcha = ReCaptchaField(widget=ReCaptchaV2Checkbox)

class Machine_Form(forms.ModelForm):
    class Meta:
        model = Machine
        fields = ('author', 'user', 'fqdn', 'wdir', 'installDir', 'dataDir')
        widgets = {
            'author': forms.HiddenInput(),
        }

    def clean(self):
        cleaned_data = super().clean()
        user = cleaned_data.get('user')
        fqdn = cleaned_data.get('fqdn')
        author = cleaned_data.get('author')

        if Machine.objects.filter(author=author, user=user, fqdn=fqdn).exists():
            raise ValidationError('A machine with this author, user and FQDN already exists.')

        return cleaned_data


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



class Key_Gen_Form(forms.ModelForm):
    class Meta:
        model = Key_Gen
        fields = ('author', 'machine', 'public_key', 'private_key')

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

