import {customElement, html, LitElement, property} from 'lit-element';
import {MDCTextField} from '@material/textfield';

// @ts-ignore
import VerifyStyle from '../style/verify.scss';
// @ts-ignore
import SrcomStyle from '../style/loginsrcom.scss';

@customElement('login-srcom')
export class LoginSrcom extends LitElement {

    @property({type: String})
    private discordId: String | undefined;

    private userInfo: any;

    @property({type: String})
    private errorMessage: String | undefined;

    protected render() {
        this.fetchUserInformation();
        return html`
<header><h6>Login with Speedrun.com</h6></header>
<div class="c-centerCard">
    <div class="c-parentCard mdc-card">
        <h1>Add Speedrun.com Account to ${this.userInfo.discrimName}</h1>
        <hr>
        <span style="display: inline">Please retrieve your API key from <a href="https://www.speedrun.com/settings/api" style="display: inline">www.speedrun.com/settings/api</a></span>
        <div class="mdc-text-field" id="api-key" style="margin-top: 1em">
            <input type="text" id="api-key-field" class="mdc-text-field__input">
            <label class="mdc-floating-label" for="api-key-field">API Key</label>
            <div class="mdc-line-ripple"></div>
        </div>
        <button @click="${this.submitKey}" class="c-addButton c-gold mdc-button--raised"><span class="mdc-button__label">Verify</span></button>
        ${this.errorMessage ? html`<div class="mdc-card" style="margin: 1em 1em 1em 1em;background-color: darkred;color: white;width: min-content;display: inline-table;overflow: hidden;white-space: nowrap;padding: 1em;">${this.errorMessage}</div>` : ''}
    </div>
</div>`
    }

    async submitKey() {
        if (this.shadowRoot) {
            let keyField = <HTMLInputElement>this.shadowRoot.getElementById('api-key-field');
            if (keyField.value) {
                fetch('/user/' + this.discordId + '/connections/srcom', {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'text/plain'
                    },
                    body: keyField.value
                })
                    .then(res => res.json())
                    .then(res => {
                        if (res.success) {
                            window.location.assign('/');
                        } else {
                            this.errorMessage = "Error: Invalid API Key. Please try again."
                            console.log(this.errorMessage);
                        }
                    });
                console.log(keyField.value);
            }
        }
    }

    protected fetchUserInformation() {
        let request = new XMLHttpRequest();
        console.log(this.discordId);
        request.open('GET', '/user/' + this.discordId, false);
        request.send();
        this.userInfo = JSON.parse(request.response);
    }

    // @ts-ignore
    firstUpdated(_changedProperties) {
        // @ts-ignore
        MDCTextField.attachTo(this.shadowRoot.getElementById('api-key'));
    }

    static get styles() {
        return [SrcomStyle, VerifyStyle];
    }
}